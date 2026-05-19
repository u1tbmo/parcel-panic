package parcelpanic.screens;

import java.util.List;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import parcelpanic.input.InputAction;
import parcelpanic.input.InputHintProvider;
import parcelpanic.input.LocalPlayerController;
import parcelpanic.logic.GameSimulation;
import parcelpanic.media.AssetKeys.ColorKey;
import parcelpanic.media.AssetKeys.FontKey;
import parcelpanic.media.UiFactory;
import parcelpanic.net.GameClient;
import parcelpanic.screen.ContentScreen;
import parcelpanic.shared.GameState;
import parcelpanic.shared.ParcelState;
import parcelpanic.shared.PlayerIntent;
import parcelpanic.video.VideoManager;
import parcelpanic.view.GameRenderer;
import parcelpanic.world.MapLoader;
import parcelpanic.world.TileMap;

public final class MatchScreen extends ContentScreen {
  private Label timerLabel;
  private Label moneyLabel;
  private Label parcelListLabel;
  private BorderPane rootPane;

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
    rootPane.setLeft(createSidePane("ACTIVE PACKAGES"));
    rootPane.setRight(createSidePane("FAILED / MISSED"));
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

  private VBox createSidePane(String title) {
    Font titleFont = ctx.assets().getFont(FontKey.LABEL);
    Font bodyFont = ctx.assets().getFont(FontKey.LABEL);

    Label titleLabel = UiFactory.createLabel(title, titleFont, textColor);

    parcelListLabel = UiFactory.createLabel("No packages yet", bodyFont, textColor);
    parcelListLabel.setWrapText(true);

    VBox pane = new VBox(12, titleLabel, parcelListLabel);
    pane.setAlignment(Pos.TOP_CENTER);
    pane.setPadding(new Insets(20));
    pane.setPrefWidth(150);
    pane.setStyle(
        "-fx-background-color: rgba(0, 0, 0, 0.55);"
            + "-fx-border-color: white;"
            + "-fx-border-width: 2;");

    return pane;
  }

  private VBox createBottomContainer() {
    Font hintFont = ctx.assets().getFont(FontKey.LABEL);
    Font iconFont = ctx.assets().getFont(FontKey.HINT);
    Color hintColor = ctx.assets().getColor(ColorKey.TEXT_HINT);

    Node pauseHint =
        UiFactory.createHint(
            InputHintProvider.getIconForAction(InputAction.PAUSE, ctx.settings().controls()),
            "Pause",
            iconFont,
            hintFont,
            hintColor);

    VBox container = new VBox(pauseHint);
    container.setAlignment(Pos.BOTTOM_CENTER);
    container.setPadding(new Insets(0, 0, 20, 0));
    return container;
  }

  @Override
  public void fixedUpdate(double dt) {
    int playerId = 0;

    if (gameClient != null) {
      if (!gameClient.isRunning() && !kicked) {
        kicked = true;
        Platform.runLater(() -> {
          ctx.navigator().push(new KickOverlay("Host Exited"));
        });
        return;
      }
      playerId = Math.max(0, gameClient.getPlayerId() - 1);
    }

    PlayerIntent intent = inputController.createIntent(playerId, ctx.input());

    if (gameClient != null) {
      if (gameClient.isRunning()) {
        gameClient.sendIntent(intent);
      }
      return;
    }

    localState = simulation.update(dt, List.of(intent));

    if (localState.matchTimer() <= 0) {
      ctx.navigator().requestSwitch(new ResultsScreen(1234));
    }
  }

  @Override
  public void render(double alpha) {
    GameState state = getRenderableState();

    updateHud(state);

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

  private void updateHud(GameState state) {
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

    if (parcelListLabel != null) {
      parcelListLabel.setText(buildParcelListText(state));
    }
  }

  private String buildParcelListText(GameState state) {
    if (state.parcels() == null || state.parcels().isEmpty()) {
      return "No packages yet";
    }

    StringBuilder builder = new StringBuilder();

    for (ParcelState parcel : state.parcels()) {
      builder
          .append("Package ")
          .append(parcel.id())
          .append("\nTarget: ")
          .append(parcel.targetHouseId())
          .append("\nTime: ")
          .append((int) parcel.remainingTime())
          .append("s\n\n");
    }

    return builder.toString();
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