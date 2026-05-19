package parcelpanic.screens;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import parcelpanic.input.InputAction;
import parcelpanic.input.LocalPlayerController;
import parcelpanic.logic.GameSimulation;
import parcelpanic.logic.MatchRules;
import parcelpanic.media.AssetKeys.AudioKey;
import parcelpanic.media.AssetKeys.ColorKey;
import parcelpanic.media.AssetKeys.FontKey;
import parcelpanic.media.AssetKeys.ImageKey;
import parcelpanic.media.UiFactory;
import parcelpanic.net.GameClient;
import parcelpanic.screen.ContentScreen;
import parcelpanic.shared.GameState;
import parcelpanic.shared.ParcelState;
import parcelpanic.shared.PlayerIntent;
import parcelpanic.shared.VehicleState;
import parcelpanic.util.SmoothedValue;
import parcelpanic.video.VideoManager;
import parcelpanic.view.GameRenderer;
import parcelpanic.view.SlidingCenterText;
import parcelpanic.world.MapLoader;
import parcelpanic.world.TileMap;

public final class MatchScreen extends ContentScreen {
  private Label timerLabel;
  private Label moneyLabel;
  private HBox orderCardsBox;
  private BorderPane rootPane;

  private final List<OrderCardUI> activeCards = new ArrayList<>();

  private static class OrderCardUI {
    final int parcelId;
    final VBox card;
    final SmoothedValue yOffset;
    final SmoothedValue carrierAnim;
    boolean isExiting = false;
    boolean hasCarrier = false;
    boolean wasInPulseZone = false;

    OrderCardUI(int parcelId, VBox card) {
      this.parcelId = parcelId;
      this.card = card;
      this.yOffset = new SmoothedValue(-100.0, 0.2); // Start off-screen (top)
      this.carrierAnim = new SmoothedValue(0.0, 0.3); // Animation for expanding carrier info
    }
  }

  private Color textColor;
  private Color surfaceDark;

  private TileMap tileMap;
  private GameRenderer gameRenderer;
  private Canvas gameCanvas;

  private SlidingCenterText countdownText;
  private boolean preGameCountdown = true;
  private int countdownStep = 3;
  private double countdownTimer = 0.0;

  private SlidingCenterText endText;
  private boolean ending = false;
  private boolean endWasFailure = false;

  private SlidingCenterText lastChanceText;
  private boolean lastChanceShown = false;
  private GameState endState;

  private GameSimulation simulation;
  private LocalPlayerController inputController;
  private GameState localState;
  private final GameClient gameClient;
  private boolean kicked = false;

  public MatchScreen() {
    this.gameClient = null;
  }

  public MatchScreen(GameClient gameClient) {
    this.gameClient = gameClient;
  }

  @Override
  protected VideoManager.ViewportMode viewportMode() {
    return VideoManager.ViewportMode.FIXED_16_9;
  }

  @Override
  protected void onBeforeBuild() {
    this.textColor = ctx.assets().getColor(ColorKey.TEXT_LIGHT);
    this.surfaceDark = ctx.assets().getColor(ColorKey.SURFACE_DARK);

    try {
      this.tileMap = MapLoader.loadFromText("/maps/map.txt");
    } catch (Exception e) {
      System.err.println("Map failed to load, using default.");
      this.tileMap = new TileMap(20, 15);
    }

    this.gameRenderer = new GameRenderer(ctx.assets(), ctx.settings().controls());
    this.simulation = new GameSimulation(tileMap, ctx.audio()::playSound);
    this.inputController = new LocalPlayerController();

    String playerName = ctx.settings().playerName();
    simulation.addPlayer(0, 0, 0, playerName);
    if (gameClient != null) {
      simulation.addPlayer(1, 0, 0, "Player 2");
    }

    this.localState = simulation.generateSnapshot();

    // Reset countdown state on screen creation.
    this.preGameCountdown = true;
    this.countdownStep = 3;
    this.countdownTimer = 0.0;
  }

  @Override
  protected Node createContent() {
    buildUI();

    int tileSize = 40;
    this.gameCanvas = new Canvas(tileMap.getWidth() * tileSize, tileMap.getHeight() * tileSize);

    rootPane.setMouseTransparent(false);
    rootPane.setPickOnBounds(false);

    StackPane gameLayer = new StackPane();
    gameLayer.getChildren().addAll(gameCanvas, rootPane);
    gameLayer.setAlignment(Pos.CENTER);

    // Countdown banner on top of the whole screen.
    countdownText = new SlidingCenterText(ctx.assets().getFont(FontKey.DISPLAY), Color.WHITE);
    countdownText.setOffscreenX(VideoManager.LOGICAL_WIDTH * 0.65);
    // Enter + hold + exit ~= 1 second per number.
    countdownText.setTimings(0.2, 0.6, 0.2);
    countdownText.setSmoothing(0.45);
    gameLayer.getChildren().add(countdownText);

    // End banner on top of the whole screen.
    endText = new SlidingCenterText(ctx.assets().getFont(FontKey.DISPLAY), Color.WHITE);
    endText.setOffscreenX(VideoManager.LOGICAL_WIDTH * 0.65);
    endText.setTimings(0.2, 1.0, 0.2);
    endText.setSmoothing(0.45);
    gameLayer.getChildren().add(endText);

    // Last chance warning banner.
    lastChanceText = new SlidingCenterText(ctx.assets().getFont(FontKey.DISPLAY), Color.web("#ffcc00"));
    lastChanceText.setOffscreenX(VideoManager.LOGICAL_WIDTH * 0.65);
    lastChanceText.setTimings(0.3, 1.5, 0.3);
    lastChanceText.setSmoothing(0.45);
    gameLayer.getChildren().add(lastChanceText);
    lastChanceShown = false;

    // Kick off the first banner immediately.
    countdownText.play(String.valueOf(countdownStep));
    ctx.audio().playSound(AudioKey.COUNTDOWN_TICK);
    countdownStep--;

    return gameLayer;
  }

  private void buildUI() {
    rootPane = UiFactory.createBorderPane(VideoManager.LOGICAL_WIDTH, VideoManager.LOGICAL_HEIGHT);
    rootPane.setMouseTransparent(true);

    rootPane.setTop(createTopHud());
    rootPane.setBottom(createBottomContainer());
  }

  private HBox createTopHud() {
    orderCardsBox = new HBox(12);
    orderCardsBox.setAlignment(Pos.TOP_CENTER);
    orderCardsBox.setFillHeight(false);
    orderCardsBox.setPadding(new Insets(0, 0, 0, 0));

    return orderCardsBox;
  }

  private HBox createBottomContainer() {
    Font font = ctx.assets().getFont(FontKey.TITLE);

    DropShadow shadow = new DropShadow(4.0, Color.rgb(0, 0, 0, 0.6));

    moneyLabel = UiFactory.createLabel("$0", font, textColor);
    moneyLabel.setPadding(new Insets(0, 0, 0, 20));
    moneyLabel.setEffect(shadow);
    timerLabel = UiFactory.createLabel("Time: 180", font, textColor);
    timerLabel.setPadding(new Insets(0, 20, 0, 0));
    timerLabel.setEffect(shadow);

    Region spacer = new Region();

    HBox bottomHud = new HBox(moneyLabel, spacer, timerLabel);
    bottomHud.setAlignment(Pos.BOTTOM_CENTER);
    bottomHud.setPadding(new Insets(0, 0, 16, 0));
    bottomHud.setPrefHeight(50);

    HBox.setHgrow(spacer, Priority.ALWAYS);

    return bottomHud;
  }

  @Override
  public void fixedUpdate(double dt) {
    if (countdownText != null) {
      countdownText.fixedUpdate(dt);
    }

    if (endText != null) {
      endText.fixedUpdate(dt);
    }

    if (lastChanceText != null) {
      lastChanceText.fixedUpdate(dt);
    }

    if (ending) {
      // Let the end banner play, then switch.
      if (endText == null || !endText.isPlaying()) {
        ctx.navigator().requestSwitch(new ResultsScreen(endWasFailure, endState));
      }
      return;
    }

    if (preGameCountdown) {
      tickCountdown(dt);
      return;
    }

    int playerId = 0;

    if (gameClient != null) {
      if (!gameClient.isRunning() && !kicked) {
        // If the client stops running, check if it was due to a normal game end
        GameState latest = gameClient.getLatestState();
        if (latest != null && (latest.matchTimer() <= 0 || latest.unhappiness() >= 100)) {
          beginEnding(latest);
        } else {
          kicked = true;
          ctx.audio().stopMusic();
          Platform.runLater(
              () -> {
                ctx.navigator().push(new KickOverlay("Host Exited"));
              });
        }
        return;
      }
      playerId = Math.max(0, gameClient.getPlayerId() - 1);
    }

    PlayerIntent intent = inputController.createIntent(playerId, ctx.input());

    if (gameClient != null) {
      if (gameClient.isRunning()) {
        gameClient.sendIntent(intent);

        // Also check if state dictates game end while running
        GameState latest = gameClient.getLatestState();
        if (latest != null && (latest.matchTimer() <= 0 || latest.unhappiness() >= 100.0)) {
          beginEnding(latest);
        }
      }
      return;
    }

    localState = simulation.update(dt, List.of(intent));

    if (localState.matchTimer() <= 0 || localState.unhappiness() >= 100.0) {
      beginEnding(localState);
    }
  }

  @Override
  public void render(double alpha) {
    GameState state = getRenderableState();

    updateHud(state, alpha);

    if (gameCanvas != null && gameRenderer != null) {
      GraphicsContext gc = gameCanvas.getGraphicsContext2D();
      gc.clearRect(0, 0, gameCanvas.getWidth(), gameCanvas.getHeight());
      gameRenderer.render(gc, state, alpha);
    }

    // Play server-broadcasted sounds in multiplayer
    if (gameClient != null) {
      String soundName;
      while ((soundName = gameClient.pollSound()) != null) {
        try {
          AudioKey key = AudioKey.valueOf(soundName);
          ctx.audio().playSound(key);
        } catch (IllegalArgumentException e) {
          System.err.println("Unknown sound: " + soundName);
        }
      }
    }

    if (countdownText != null) {
      countdownText.render(alpha);
    }

    if (endText != null) {
      endText.render(alpha);
    }

    if (lastChanceText != null) {
      lastChanceText.render(alpha);
    }
  }

  private void beginEnding(GameState state) {
    if (ending) return;

    this.ending = true;
    this.endState = state;
    this.endWasFailure = state.unhappiness() >= 100.0;

    ctx.audio().stopMusic();

    if (endText != null) {
      endText.play(endWasFailure ? "You failed" : "Time's up");
    }
  }

  private void tickCountdown(double dt) {
    if (countdownText == null) {
      preGameCountdown = false;
      return;
    }

    // If a banner is currently sliding/holding, wait for it to finish.
    if (countdownText.isPlaying()) {
      return;
    }

    // Small gap between banners.
    countdownTimer -= dt;
    if (countdownTimer > 0.0) {
      return;
    }

    if (countdownStep > 0) {
      countdownText.play(String.valueOf(countdownStep));
      ctx.audio().playSound(AudioKey.COUNTDOWN_TICK);
      countdownStep--;
      countdownTimer = 0.05;
      return;
    }

    // Final banner.
    countdownText.play("Go!");
    ctx.audio().playSound(AudioKey.COUNTDOWN_TICK);
    countdownStep = -1;
    countdownTimer = 0.05;

    // Start background music on loop
    ctx.audio().playMusic(AudioKey.BACKGROUND_MUSIC);

    // Allow the game to start as soon as "Go!" begins.
    preGameCountdown = false;
  }

  private GameState getRenderableState() {
    if (gameClient != null) {
      GameState state = gameClient.getLatestState();

      if (state == null) {
        return localState;
      }

      if (state.map() == null) {
        return new GameState(
            state.matchTimer(),
            state.unhappiness(),
            state.score(),
            state.deliveredCount(),
            state.expiredCount(),
            state.vehicles(),
            state.parcels(),
            tileMap);
      }

      return state;
    }

    return localState;
  }

  private void updateHud(GameState state, double alpha) {
    if (state == null) {
      return;
    }

    if (timerLabel != null) {
      int seconds = Math.max(0, (int) state.matchTimer());
      int minutes = seconds / 60;
      int remainingSeconds = seconds % 60;

      timerLabel.setText(String.format("Time: %02d:%02d", minutes, remainingSeconds));

      if (seconds < 30) {
        timerLabel.setTextFill(Color.RED);
      } else {
        timerLabel.setTextFill(textColor);
      }
    }

    if (moneyLabel != null) {
      moneyLabel.setText("$" + (int) state.score());
    }

    if (orderCardsBox != null) {
      updateOrderCards(state, alpha);
    }

    if (lastChanceText != null && !lastChanceShown && state.unhappiness() >= MatchRules.MAX_UNHAPPINESS - MatchRules.PENALTY_PER_EXPIRATION) {
      lastChanceShown = true;
      lastChanceText.play("Last chance!");
    }
  }

  private void updateOrderCards(GameState state, double alpha) {
    if (state.parcels() == null) return;

    List<ParcelState> parcels = state.parcels();

    for (OrderCardUI ui : activeCards) {
      ui.isExiting = true;
    }

    for (ParcelState parcel : parcels) {
      OrderCardUI existingUi = null;
      for (OrderCardUI ui : activeCards) {
        if (ui.parcelId == parcel.id()) {
          existingUi = ui;
          break;
        }
      }

      if (existingUi != null) {
        existingUi.isExiting = false;
        updateOrderCard(existingUi, parcel, state);
      } else {
        VBox newCard = createEmptyOrderCard();
        OrderCardUI newUi = new OrderCardUI(parcel.id(), newCard);
        updateOrderCard(newUi, parcel, state);
        activeCards.add(newUi);
        orderCardsBox.getChildren().add(0, newCard);
      }
    }

    for (int i = activeCards.size() - 1; i >= 0; i--) {
      OrderCardUI ui = activeCards.get(i);

      // Update target based on state
      ui.yOffset.update(ui.isExiting ? -100.0 : 0.0);
      ui.carrierAnim.update(ui.hasCarrier ? 1.0 : 0.0);

      double currentY = ui.yOffset.get(alpha);
      ui.card.setTranslateY(currentY);

      double cAnim = ui.carrierAnim.get(alpha);
      HBox carrierBox = (HBox) ui.card.lookup("#carrierBox");
      if (carrierBox != null) {
        if (cAnim > 0.01) {
          carrierBox.setVisible(true);
          carrierBox.setManaged(true);
          carrierBox.setOpacity(cAnim);
          double h = 24.0 * cAnim;
          carrierBox.setPrefHeight(h);
          carrierBox.setMinHeight(h);
          carrierBox.setMaxHeight(h);
        } else {
          carrierBox.setVisible(false);
          carrierBox.setManaged(false);
          carrierBox.setPrefHeight(0);
          carrierBox.setMinHeight(0);
          carrierBox.setMaxHeight(0);
        }
      }

      if (ui.isExiting && ui.yOffset.getCurrent() <= -99.0) {
        orderCardsBox.getChildren().remove(ui.card);
        activeCards.remove(i);
      }
    }
  }

  private VBox createEmptyOrderCard() {
    VBox card = new VBox(4);
    card.setAlignment(Pos.TOP_CENTER);
    card.setPadding(new Insets(4, 8, 8, 8));
    card.setPrefWidth(140);
    card.setMinHeight(32);

    // Progress bar container
    StackPane progressContainer = new StackPane();
    progressContainer.setAlignment(Pos.CENTER_LEFT);
    progressContainer.setPrefSize(124, 12);
    progressContainer.setStyle("-fx-background-color: #ddd;");

    Rectangle progressBar = new Rectangle(124, 12);
    progressBar.setFill(Color.web(ColorKey.SUCCESS.getHex()));
    progressBar.setId("progressBar");

    progressContainer.getChildren().add(progressBar);

    ImageView parcelIcon = new ImageView();
    parcelIcon.setFitWidth(20);
    parcelIcon.setFitHeight(20);
    parcelIcon.setPreserveRatio(true);
    parcelIcon.setId("parcelIcon");

    Font titleFont = ctx.assets().getFont(FontKey.BODY);
    Label targetLabel =
        UiFactory.createLabel("House 0", titleFont, Color.web(ColorKey.TEXT.getHex()));
    targetLabel.setId("targetLabel");

    HBox contentBox = new HBox(4, parcelIcon, targetLabel);
    contentBox.setAlignment(Pos.CENTER);
    contentBox.setId("contentBox");

    // Carrier info box (hidden by default)
    ImageView carrierIcon = new ImageView();
    carrierIcon.setFitWidth(14);
    carrierIcon.setFitHeight(14);
    carrierIcon.setPreserveRatio(true);
    carrierIcon.setViewport(new javafx.geometry.Rectangle2D(0, 0, 22, 22));
    carrierIcon.setId("carrierIcon");

    Label carrierLabel = UiFactory.createLabel("", titleFont, Color.web(ColorKey.TEXT.getHex()));
    carrierLabel.setId("carrierLabel");

    HBox carrierBox = new HBox(6, carrierIcon, carrierLabel);
    carrierBox.setAlignment(Pos.CENTER);
    carrierBox.setId("carrierBox");
    carrierBox.setManaged(false);
    carrierBox.setVisible(false);
    carrierBox.setOpacity(0.0);
    carrierBox.setPrefHeight(0);
    carrierBox.setMinHeight(0);
    carrierBox.setMaxHeight(0);

    card.getChildren().addAll(progressContainer, contentBox, carrierBox);
    card.setTranslateY(100.0); // Start off-screen
    return card;
  }

  private void updateOrderCard(OrderCardUI ui, ParcelState parcel, GameState state) {
    VBox card = ui.card;
    StackPane progressContainer = (StackPane) card.getChildren().get(0);
    Rectangle progressBar = (Rectangle) progressContainer.lookup("#progressBar");
    HBox contentBox = (HBox) card.lookup("#contentBox");
    ImageView parcelIcon = (ImageView) contentBox.lookup("#parcelIcon");
    Label targetLabel = (Label) contentBox.lookup("#targetLabel");

    HBox carrierBox = (HBox) card.lookup("#carrierBox");
    ImageView carrierIcon = (ImageView) carrierBox.lookup("#carrierIcon");
    Label carrierLabel = (Label) carrierBox.lookup("#carrierLabel");

    targetLabel.setText("House " + parcel.targetHouseId());

    if (parcelIcon.getImage() == null) {
      parcelIcon.setImage(ctx.assets().getImage(ImageKey.ENTITY_PARCEL));
    }

    if (parcel.carrierId() != null) {
      parcelpanic.shared.VehicleState carrier = null;
      if (state.vehicles() != null) {
        for (parcelpanic.shared.VehicleState v : state.vehicles()) {
          if (v.id() == parcel.carrierId()) {
            carrier = v;
            break;
          }
        }
      }

      if (carrier != null) {
        carrierLabel.setText(
            carrier.playerName() != null ? carrier.playerName() : "Player " + (carrier.id() + 1));
        carrierIcon.setImage(
            ctx.assets()
                .getImage(GameRenderer.getImageKeyForVehicle(carrier.id(), carrier.colorIndex())));
        ui.hasCarrier = true;
      } else {
        ui.hasCarrier = false;
      }
    } else {
      ui.hasCarrier = false;
    }

    double maxTime = MatchRules.MAX_PARCEL_TIME;
    double remaining = Math.max(0, parcel.remainingTime());
    double percentage = remaining / maxTime;

    progressBar.setWidth(124 * percentage);

    if (percentage > 0.5) {
      progressBar.setFill(Color.web(ColorKey.SUCCESS.getHex()));
    } else if (percentage > 0.25) {
      progressBar.setFill(Color.web(ColorKey.WARNING.getHex()));
    } else {
      progressBar.setFill(Color.web(ColorKey.DANGER.getHex()));
    }

    if (percentage <= 0.25) {
      double time = System.currentTimeMillis() / 1000.0;
      double pulse = (Math.sin(time * 10) + 1) / 2.0;
      boolean pulseOn = pulse > 0.5;

      // Play warning sounds on pulse transitions
      if (pulseOn && !ui.wasInPulseZone) {
        ctx.audio().playSound(AudioKey.PARCEL_WARNING_ON);
      } else if (!pulseOn && ui.wasInPulseZone) {
        ctx.audio().playSound(AudioKey.PARCEL_WARNING_OFF);
      }
      ui.wasInPulseZone = pulseOn;

      // Interpolate background between off-white #FFF9E6 and light red #FFD0D0
      int r = 255;
      int g = (int) (249 - (249 - 208) * pulse);
      int b = (int) (230 - (230 - 208) * pulse);
      String bgColor = String.format("#%02X%02X%02X", r, g, b);

      card.setStyle(
          "-fx-background-color: "
              + bgColor
              + ";"
              + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 10, 0, 0, -2);");
    } else {
      ui.wasInPulseZone = false;
      card.setStyle(
          "-fx-background-color: #FFF9E6;"
              + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 10, 0, 0, -2);");
    }
  }

  @Override
  public void onKeyPressed(InputAction action) {
    if (action == InputAction.PAUSE) {
      ctx.navigator().push(new PauseOverlay());
    }
  }

  @Override
  protected void onBeforeExit() {
    ctx.audio().stopMusic();
    if (gameClient != null) {
      gameClient.disconnect();
    }
    // Stop the active local server if we are the host
    parcelpanic.net.GameServer server = parcelpanic.net.GameServer.getActiveServer();
    if (server != null) {
      server.stop();
    }
  }
}
