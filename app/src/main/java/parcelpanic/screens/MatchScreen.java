package parcelpanic.screens;

import java.util.List;
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
import parcelpanic.input.LocalPlayerController;
import parcelpanic.logic.GameSimulation;
import parcelpanic.logic.entities.ParcelLogic;
import parcelpanic.media.AssetKeys.ColorKey;
import parcelpanic.media.AssetKeys.FontKey;
import parcelpanic.media.UiFactory;
import parcelpanic.net.GameClient;
import parcelpanic.screen.ContentScreen;
import parcelpanic.shared.GameState;
import parcelpanic.shared.PlayerIntent;
import parcelpanic.video.VideoManager;
import parcelpanic.view.GameRenderer;
import parcelpanic.world.MapLoader;
import parcelpanic.world.TileMap;

public final class MatchScreen extends ContentScreen {
  private final GameClient gameClient;

  private Label timerLabel;
  private Label scoreLabel;

  private TileMap tileMap;
  private GameRenderer gameRenderer;
  private Canvas gameCanvas;

  private GameSimulation simulation;
  private LocalPlayerController inputController;

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
    try {
      this.tileMap = MapLoader.loadFromText("/maps/map.txt");
    } catch (Exception e) {
      System.err.println("Map failed to load, using default.");
      this.tileMap = new TileMap(20, 15);
    }
    this.gameRenderer = new GameRenderer(ctx.assets(), ctx.settings().controls());
    this.inputController = new LocalPlayerController();

    if (gameClient == null) {
      this.simulation = new GameSimulation(tileMap);
      // Initial game state setup
      simulation.addPlayer(1, 300, 300);
      simulation.addParcel(new ParcelLogic(1, 1, 620, 260));
    }
  }

  @Override
  protected Node createContent() {
    int tileSize = 40;
    this.gameCanvas = new Canvas(tileMap.getWidth() * tileSize, tileMap.getHeight() * tileSize);

    StackPane gameLayer = new StackPane();
    gameLayer.getChildren().add(gameCanvas);
    gameLayer.setAlignment(Pos.CENTER);

    BorderPane rootPane =
        UiFactory.createBorderPane(VideoManager.LOGICAL_WIDTH, VideoManager.LOGICAL_HEIGHT);
    rootPane.setMouseTransparent(true);
    rootPane.setPickOnBounds(false);

    Font font = ctx.assets().getFont(FontKey.LABEL, 20);
    Color textColor = ctx.assets().getColor(ColorKey.TEXT_LIGHT);

    timerLabel = UiFactory.createLabel("03:00", font, textColor);
    scoreLabel = UiFactory.createLabel("$0", font, textColor);

    VBox hudBox = new VBox(5);
    hudBox.setAlignment(Pos.TOP_CENTER);
    hudBox.setPadding(new Insets(10, 0, 0, 0));
    hudBox.getChildren().addAll(timerLabel, scoreLabel);

    rootPane.setTop(hudBox);

    StackPane finalLayer = new StackPane();
    finalLayer.getChildren().addAll(gameLayer, rootPane);

    return finalLayer;
  }

  @Override
  public void fixedUpdate(double dt) {
    if (gameClient != null) {
      int activePlayerId = gameClient.getPlayerId();
      if (activePlayerId != -1) {
        PlayerIntent intent = inputController.createIntent(activePlayerId, ctx.input());
        gameClient.sendIntent(intent);
      }
      GameState state = gameClient.getLatestState();
      if (state != null && state.matchTimer() <= 0) {
        ctx.navigator().requestSwitch(new ResultsScreen((int) state.score()));
      }
    } else {
      PlayerIntent intent = inputController.createIntent(1, ctx.input());
      simulation.update(dt, List.of(intent));

      if (simulation.getMatchTimer() <= 0) {
        ctx.navigator().requestSwitch(new ResultsScreen(1234));
      }
    }
  }

  @Override
  public void render(double alpha) {
    GameState state = null;
    if (gameClient != null) {
      state = gameClient.getLatestState();
    } else {
      state = simulation.generateSnapshot();
    }

    if (state == null) {
      if (gameCanvas != null) {
        GraphicsContext gc = gameCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, gameCanvas.getWidth(), gameCanvas.getHeight());
        gc.setFill(Color.WHITE);
        gc.fillText(
            "Waiting for server state...",
            gameCanvas.getWidth() / 2 - 80,
            gameCanvas.getHeight() / 2);
      }
      return;
    }

    if (timerLabel != null && scoreLabel != null) {
      int totalSeconds = Math.max(0, (int) state.matchTimer());
      int minutes = totalSeconds / 60;
      int seconds = totalSeconds % 60;

      timerLabel.setText(String.format("%02d:%02d", minutes, seconds));
      scoreLabel.setText(String.format("$%d", (int) state.score()));
    }

    if (gameCanvas != null && gameRenderer != null) {
      GraphicsContext gc = gameCanvas.getGraphicsContext2D();
      gc.clearRect(0, 0, gameCanvas.getWidth(), gameCanvas.getHeight());
      gameRenderer.render(gc, state, alpha);
    }
  }

  @Override
  protected void onBeforeExit() {
    if (gameClient != null) {
      gameClient.disconnect();
    }
  }

  @Override
  public void onKeyPressed(InputAction action) {
    if (action == InputAction.PAUSE) {
      ctx.navigator().push(new PauseOverlay());
    }
  }
}
