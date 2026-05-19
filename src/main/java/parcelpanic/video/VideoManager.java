package parcelpanic.video;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.stage.Screen;
import javafx.stage.Stage;
import parcelpanic.settings.GameSettings;

public final class VideoManager {

  public enum ViewportMode {
    FIXED_16_9,
    FULL_WINDOW,
  }

  /// The internal width the game logic uses for rendering.
  public static final double LOGICAL_WIDTH = 1280.0;
  /// The internal height the game logic uses for rendering.
  public static final double LOGICAL_HEIGHT = 720.0;

  private final Stage stage;
  private final Scene scene;
  private final StackPane root;
  private final Pane canvas;
  private final StackPane canvasContainer;

  private int windowWidth;
  private int windowHeight;
  private DisplayMode currentDisplayMode;
  private ViewportMode viewportMode;

  private double gameViewportWidth;
  private double gameViewportHeight;

  public VideoManager(Stage stage, Scene scene, StackPane root, Pane canvas) {
    this.stage = stage;
    this.scene = scene;
    this.root = root;
    this.canvas = canvas;
    this.windowWidth = (int) scene.getWidth();
    this.windowHeight = (int) scene.getHeight();
    this.currentDisplayMode = DisplayMode.WINDOWED;
    this.viewportMode = ViewportMode.FIXED_16_9;

    // Use a fixed logical size for the canvas
    setPaneSize(canvas, LOGICAL_WIDTH, LOGICAL_HEIGHT);

    javafx.scene.shape.Rectangle clip =
        new javafx.scene.shape.Rectangle(LOGICAL_WIDTH, LOGICAL_HEIGHT);
    canvas.setClip(clip);

    // Wrap canvas in a container for centering when scaled
    this.canvasContainer = new StackPane(canvas);
    this.canvasContainer.setPickOnBounds(false); // Let clicks pass through if empty

    // Replace canvas in root with the container
    root.getChildren().remove(canvas);
    root.getChildren().add(canvasContainer);

    calculateViewport();
  }

  /// Apply a resolution change
  public void applyResolution(int width, int height) {
    if (this.windowWidth == width && this.windowHeight == height) {
      return;
    }

    this.windowWidth = width;
    this.windowHeight = height;

    // Update the background size
    Region background = (Region) root.getChildren().get(0);
    setRegionSize(background, width, height);

    // Keep the existing scene/root and resize the stage.
    if (stage.isShowing()) {
      stage.setWidth(stage.getWidth() - scene.getWidth() + width);
      stage.setHeight(stage.getHeight() - scene.getHeight() + height);
    } else {
      stage.setWidth(width);
      stage.setHeight(height);
    }

    if (currentDisplayMode == DisplayMode.WINDOWED) {
      stage.centerOnScreen();
      if (stage.isShowing()) {
        stage.toFront();
        stage.requestFocus();
      }
    }

    calculateViewport();
  }

  /// Apply display mode change
  public void applyDisplayMode(DisplayMode mode) {
    if (currentDisplayMode == mode) {
      return;
    }

    switch (mode) {
      case WINDOWED -> applyWindowed(windowWidth, windowHeight);
      case BORDERLESS -> applyBorderless(windowWidth, windowHeight, getPrimaryScreenResolution());
    }

    currentDisplayMode = mode;
    calculateViewport();
  }

  /// Apply both resolution and display mode
  public void applySettings(GameSettings.VideoSettings settings) {
    DisplayMode targetMode = settings.displayMode();
    Resolution targetResolution = resolveResolutionForMode(targetMode, settings);
    applyResolution(targetResolution.width(), targetResolution.height());
    applyDisplayMode(targetMode);
  }

  /// Get the canvas (16:9 render area)
  public Pane getCanvas() {
    return canvas;
  }

  /// Get the current scene
  public Scene getScene() {
    return scene;
  }

  /// Get game viewport width (actual 16:9 render area)
  public double getGameViewportWidth() {
    return gameViewportWidth;
  }

  /// Get game viewport height (actual 16:9 render area)
  public double getGameViewportHeight() {
    return gameViewportHeight;
  }

  /// Get native screen resolution
  public Resolution getNativeResolution() {
    return getPrimaryScreenResolution();
  }

  public void setViewportMode(ViewportMode mode) {
    if (viewportMode == mode) {
      return;
    }
    viewportMode = mode;
    calculateViewport();
  }

  /// Calculate viewport for 16:9 rendering with letterboxing/pillarboxing
  private void calculateViewport() {
    // Uniform scaling: use the same scale factor for both axes to avoid stretching.
    // We choose the scale that fits the window (Scale to Fit).
    double scale =
        Math.min((double) windowWidth / LOGICAL_WIDTH, (double) windowHeight / LOGICAL_HEIGHT);

    // In FIXED_16_9 mode, we might want to explicitly restrict the viewport
    // to the 16:9 area even if the window is wider/taller.
    // In our current uniform scale approach, canvas will already be a 16:9 box.
    gameViewportWidth = LOGICAL_WIDTH * scale;
    gameViewportHeight = LOGICAL_HEIGHT * scale;

    // Apply uniform scale transform to canvas
    canvas.setScaleX(scale);
    canvas.setScaleY(scale);
  }

  private void applyWindowed(int targetWidth, int targetHeight) {
    if (stage.isFullScreen()) {
      stage.setFullScreen(false);
      Platform.runLater(() -> restoreWindowedBounds(targetWidth, targetHeight));
      return;
    }
    restoreWindowedBounds(targetWidth, targetHeight);
  }

  private void applyBorderless(int targetWidth, int targetHeight, Resolution nativeResolution) {
    if (stage.isFullScreen()) {
      stage.setFullScreen(false);
    }

    int screenWidth = nativeResolution.width();
    int screenHeight = nativeResolution.height();
    if (targetWidth != screenWidth || targetHeight != screenHeight) {
      applyResolution(screenWidth, screenHeight);
    }

    stage.setFullScreenExitHint("");
    stage.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);
    stage.setMaximized(false);
    stage.setResizable(false);
    stage.setFullScreen(true);
  }

  private void restoreWindowedBounds(int targetWidth, int targetHeight) {
    stage.setMaximized(false);
    stage.setResizable(false);
    if (stage.isShowing()) {
      stage.setWidth(stage.getWidth() - scene.getWidth() + targetWidth);
      stage.setHeight(stage.getHeight() - scene.getHeight() + targetHeight);
    } else {
      stage.setWidth(targetWidth);
      stage.setHeight(targetHeight);
    }
    stage.centerOnScreen();
    if (stage.isShowing()) {
      stage.hide();
      stage.show();
      stage.toFront();
      stage.requestFocus();
    }
  }

  private void setPaneSize(Pane pane, double width, double height) {
    pane.setPrefSize(width, height);
    pane.setMinSize(width, height);
    pane.setMaxSize(width, height);
  }

  private void setRegionSize(Region region, double width, double height) {
    region.setPrefSize(width, height);
    region.setMinSize(width, height);
    region.setMaxSize(width, height);
  }

  private Resolution resolveResolutionForMode(
      DisplayMode mode, GameSettings.VideoSettings settings) {
    if (mode == DisplayMode.BORDERLESS) {
      return getPrimaryScreenResolution();
    }
    return settings.resolution();
  }

  private Resolution getPrimaryScreenResolution() {
    Screen primary = Screen.getPrimary();
    int width = (int) primary.getBounds().getWidth();
    int height = (int) primary.getBounds().getHeight();
    return Resolution.findPreset(width, height);
  }
}
