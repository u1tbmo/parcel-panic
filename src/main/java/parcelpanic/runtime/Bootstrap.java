package parcelpanic.runtime;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import parcelpanic.input.InputAction;
import parcelpanic.input.InputBindings;
import parcelpanic.input.InputState;
import parcelpanic.loop.FixedStepLoop;
import parcelpanic.media.AssetKeys.ColorKey;
import parcelpanic.media.AssetRegistry;
import parcelpanic.media.AudioService;
import parcelpanic.screen.ScreenManager;
import parcelpanic.screens.MenuScreen;
import parcelpanic.settings.GameSettings;
import parcelpanic.video.VideoManager;

/// Handles the initialization and lifecycle of the game engine.
public final class Bootstrap {
  private final Stage stage;
  private AppContext context;
  private FixedStepLoop loop;

  public Bootstrap(Stage stage) {
    this.stage = stage;
  }

  /// Initializes all systems and returns the application context.
  public AppContext initialize() {
    stage.setTitle("Parcel Panic");

    // Load Settings
    GameSettings settings = new GameSettings();
    settings.load();

    int width = settings.video().resolution().width();
    int height = settings.video().resolution().height();

    // Initialize Media & UI Root
    AssetRegistry assets = new AssetRegistry();
    Color surfaceBlack = assets.getColor(ColorKey.SURFACE_BLACK);

    Pane canvas = new Pane();
    StackPane root = new StackPane();
    root.setAlignment(Pos.CENTER);

    Region background = new Region();
    background.setBackground(
        new Background(new BackgroundFill(surfaceBlack, CornerRadii.EMPTY, Insets.EMPTY)));
    background.setPrefSize(width, height);
    root.getChildren().addAll(background, canvas);

    Scene scene = new Scene(root, width, height, surfaceBlack);

    // Initialize Managers and Services
    InputState input = new InputState();
    AudioService audio = new AudioService();
    audio.setVolume(settings.audio().masterVolume());
    ScreenManager screens = new ScreenManager();
    VideoManager video = new VideoManager(stage, scene, root, canvas);

    this.context = new AppContext(stage, input, assets, audio, screens, settings, video);
    screens.bindContext(context);

    // Configure Stage & Input
    stage.setScene(video.getScene());
    video.applySettings(settings.video());
    InputBindings.bindScene(scene, input, screens, settings.controls());

    // Initialize Game Loop
    this.loop =
        new FixedStepLoop(
            60.0,
            (deltaTime) -> {
              input.updateTimers(deltaTime);
              for (InputAction action : InputAction.values()) {
                boolean isMovement =
                    action == InputAction.UI_UP
                        || action == InputAction.UI_DOWN
                        || action == InputAction.UI_LEFT
                        || action == InputAction.UI_RIGHT
                        || action == InputAction.MOVE_UP
                        || action == InputAction.MOVE_DOWN
                        || action == InputAction.MOVE_LEFT
                        || action == InputAction.MOVE_RIGHT;

                boolean shouldTrigger =
                    input.wasPressed(action)
                        || (isMovement
                            && screens.supportsInputRepeat()
                            && input.shouldRepeat(action));

                if (shouldTrigger) {
                  screens.onActionPressed(action);
                }
                if (input.wasReleased(action)) {
                  screens.onActionReleased(action);
                }
              }
              screens.fixedUpdate(deltaTime);
              audio.update();
              input.endOfTick();
            },
            (alpha) -> {
              screens.render(alpha);
            });

    return context;
  }

  /// Starts the game loop and shows the initial screen.
  public void launch() {
    if (context == null || loop == null) {
      throw new IllegalStateException("Bootstrap must be initialized before launch.");
    }

    context.navigator().start(new MenuScreen());
    loop.start();

    stage.setResizable(false);
    stage.setOnCloseRequest(
        event -> {
          loop.stop();
          context.audio().stopAll();
          context.settings().save();
        });

    stage.show();
    Platform.runLater(
        () -> {
          stage.toFront();
          stage.requestFocus();
        });
  }
}
