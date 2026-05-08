package parcelpanic.screens;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
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
import parcelpanic.screen.ContentScreen;
import parcelpanic.shared.GameState;
import parcelpanic.shared.PlayerIntent;
import parcelpanic.video.VideoManager;
import parcelpanic.view.GameRenderer;
import parcelpanic.world.MapLoader;
import parcelpanic.world.TileMap;

public final class MatchScreen extends ContentScreen {
  private Label timerLabel;
  private BorderPane rootPane;

  private Color textColor;
  private Color surfaceDark;

  private TileMap tileMap;
  private GameRenderer gameRenderer;
  private Canvas gameCanvas;

  private GameSimulation simulation;
  private LocalPlayerController inputController;
  
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
    this.gameRenderer = new GameRenderer(ctx.assets());
    this.simulation = new GameSimulation(tileMap);
    this.inputController = new LocalPlayerController();
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
    // rootPane.setBackground(
    //     UiFactory.createBackground(
    //             surfaceDark, VideoManager.LOGICAL_WIDTH, VideoManager.LOGICAL_HEIGHT)
    //         .getBackground());

    VBox centerContainer = createCenterContainer();
    VBox bottomContainer = createBottomContainer();

    rootPane.setCenter(centerContainer);
    rootPane.setBottom(bottomContainer);
  }

  private VBox createCenterContainer() {
    Font font = ctx.assets().getFont(FontKey.HEADLINE);
    Label status = UiFactory.createLabel("MATCH RUNNING", font, textColor);

    Font timerFont = ctx.assets().getFont(FontKey.TITLE);
    timerLabel = UiFactory.createLabel("Time: 180", timerFont, textColor);

    VBox container = new VBox(20);
    container.setAlignment(Pos.CENTER);
    container.getChildren().addAll(status, timerLabel);
    return container;
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
    container.setPadding(new Insets(0, 0, 60, 0));
    return container;
  }

  @Override
  public void fixedUpdate(double dt) {
    PlayerIntent intent = inputController.createIntent(0, ctx.input());
    simulation.fixedUpdate(dt, intent);

    if (simulation.getMatchTimer() <= 0) {
      ctx.navigator().requestSwitch(new ResultsScreen(1234));
    }
  }

  @Override
  public void render(double alpha) {
    GameState state = simulation.getCurrentState();

    if (timerLabel != null) {
      timerLabel.setText("Time: " + Math.max(0, (int) state.matchTimer()));
    }

    if (gameCanvas != null && gameRenderer != null) {
        GraphicsContext gc = gameCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, gameCanvas.getWidth(), gameCanvas.getHeight());
        gameRenderer.render(gc, state, alpha);
    }
  }

  @Override
  public void onKeyPressed(InputAction action) {
    if (action == InputAction.PAUSE) {
      ctx.navigator().push(new PauseOverlay());
    }
  }
}
