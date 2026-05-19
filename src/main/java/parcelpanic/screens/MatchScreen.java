package parcelpanic.screens;

import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import parcelpanic.input.InputAction;
import parcelpanic.input.LocalPlayerController;
import parcelpanic.logic.GameSimulation;
import parcelpanic.logic.MatchRules;
import parcelpanic.media.AssetKeys.ColorKey;
import parcelpanic.media.AssetKeys.FontKey;
import parcelpanic.media.AssetKeys.ImageKey;
import parcelpanic.media.UiFactory;
import parcelpanic.net.GameClient;
import parcelpanic.screen.ContentScreen;
import parcelpanic.shared.GameState;
import parcelpanic.shared.ParcelState;
import parcelpanic.shared.PlayerIntent;
import parcelpanic.util.SmoothedValue;
import parcelpanic.video.VideoManager;
import parcelpanic.view.GameRenderer;
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

    OrderCardUI(int parcelId, VBox card) {
      this.parcelId = parcelId;
      this.card = card;
      this.yOffset = new SmoothedValue(100.0, 0.2); // Start off-screen
      this.carrierAnim = new SmoothedValue(0.0, 0.3); // Animation for expanding carrier info
    }
  }

  private Color textColor;
  private Color surfaceDark;

  private TileMap tileMap;
  private GameRenderer gameRenderer;
  private Canvas gameCanvas;

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
    this.simulation = new GameSimulation(tileMap);
    this.inputController = new LocalPlayerController();

    simulation.addPlayer(0, 0, 0);
    if (gameClient != null) {
      simulation.addPlayer(1, 0, 0);
    }

    this.localState = simulation.generateSnapshot();
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

    return gameLayer;
  }

  private void buildUI() {
    rootPane = UiFactory.createBorderPane(VideoManager.LOGICAL_WIDTH, VideoManager.LOGICAL_HEIGHT);
    rootPane.setMouseTransparent(true);

    rootPane.setTop(createTopHud());
    rootPane.setBottom(createBottomContainer());
  }

  private HBox createTopHud() {
    Font font = ctx.assets().getFont(FontKey.TITLE);

    timerLabel = UiFactory.createLabel("Time: 180", font, textColor);
    moneyLabel = UiFactory.createLabel("$0", font, textColor);

    HBox topHud = new HBox(80, timerLabel, moneyLabel);
    topHud.setAlignment(Pos.TOP_CENTER);
    topHud.setPadding(new Insets(16, 0, 0, 0));

    return topHud;
  }

  private VBox createBottomContainer() {
    orderCardsBox = new HBox(12);
    orderCardsBox.setAlignment(Pos.BOTTOM_CENTER);
    orderCardsBox.setFillHeight(false);
    orderCardsBox.setPadding(new Insets(0, 0, -10, 0));

    VBox container = new VBox(15, orderCardsBox);
    container.setAlignment(Pos.BOTTOM_CENTER);
    container.setPadding(new Insets(0, 0, 0, 0));
    return container;
  }

  @Override
  public void fixedUpdate(double dt) {
    int playerId = 0;

    if (gameClient != null) {
      if (!gameClient.isRunning() && !kicked) {
        // If the client stops running, check if it was due to a normal game end
        GameState latest = gameClient.getLatestState();
        if (latest != null && (latest.matchTimer() <= 0 || latest.unhappiness() >= 100)) {
          ctx.navigator().requestSwitch(new ResultsScreen((int) latest.score()));
        } else {
          kicked = true;
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
          ctx.navigator().requestSwitch(new ResultsScreen((int) latest.score()));
        }
      }
      return;
    }

    localState = simulation.update(dt, List.of(intent));

    if (localState.matchTimer() <= 0 || localState.unhappiness() >= 100.0) {
      ctx.navigator().requestSwitch(new ResultsScreen((int) localState.score()));
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
    }

    if (moneyLabel != null) {
      moneyLabel.setText("$" + (int) state.score());
    }

    if (orderCardsBox != null) {
      updateOrderCards(state, alpha);
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
        orderCardsBox.getChildren().add(newCard);
      }
    }

    for (int i = activeCards.size() - 1; i >= 0; i--) {
      OrderCardUI ui = activeCards.get(i);

      // Update target based on state
      ui.yOffset.update(ui.isExiting ? 100.0 : 0.0);
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

      if (ui.isExiting && ui.yOffset.getCurrent() >= 99.0) {
        orderCardsBox.getChildren().remove(ui.card);
        activeCards.remove(i);
      }
    }
  }

  private VBox createEmptyOrderCard() {
    VBox card = new VBox(8);
    card.setAlignment(Pos.TOP_CENTER);
    card.setPadding(new Insets(12, 10, 16, 10));
    card.setPrefWidth(200);
    card.setMinHeight(40);

    // Progress bar container
    StackPane progressContainer = new StackPane();
    progressContainer.setAlignment(Pos.CENTER_LEFT);
    progressContainer.setPrefSize(180, 20);
    progressContainer.setStyle("-fx-background-color: #ddd;");

    Rectangle progressBar = new Rectangle(180, 20);
    progressBar.setFill(Color.web(ColorKey.SUCCESS.getHex()));
    progressBar.setId("progressBar");

    progressContainer.getChildren().add(progressBar);

    ImageView parcelIcon = new ImageView();
    parcelIcon.setFitWidth(32);
    parcelIcon.setFitHeight(32);
    parcelIcon.setPreserveRatio(true);
    parcelIcon.setId("parcelIcon");

    Font titleFont = ctx.assets().getFont(FontKey.LABEL);
    Label targetLabel =
        UiFactory.createLabel("House 0", titleFont, Color.web(ColorKey.TEXT.getHex()));
    targetLabel.setId("targetLabel");

    HBox contentBox = new HBox(8, parcelIcon, targetLabel);
    contentBox.setAlignment(Pos.CENTER);
    contentBox.setId("contentBox");

    // Carrier info box (hidden by default)
    ImageView carrierIcon = new ImageView();
    carrierIcon.setFitWidth(22);
    carrierIcon.setFitHeight(22);
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
        carrierLabel.setText("Player " + (carrier.id() + 1));
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

    progressBar.setWidth(180 * percentage);

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
