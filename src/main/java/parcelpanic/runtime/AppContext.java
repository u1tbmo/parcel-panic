package parcelpanic.runtime;

import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import parcelpanic.input.InputState;
import parcelpanic.media.AssetRegistry;
import parcelpanic.media.AudioService;
import parcelpanic.screen.ScreenManager;
import parcelpanic.settings.GameSettings;
import parcelpanic.video.VideoManager;

/// Contains the global state of the application.
public final class AppContext {
  private final Stage stage;
  private final InputState input;
  private final AssetRegistry assets;
  private final AudioService audio;
  private final ScreenManager navigator;
  private final GameSettings settings;
  private final VideoManager video;

  public AppContext(
      Stage stage,
      InputState input,
      AssetRegistry assets,
      AudioService audio,
      ScreenManager navigator,
      GameSettings settings,
      VideoManager video) {
    this.stage = stage;
    this.input = input;
    this.assets = assets;
    this.audio = audio;
    this.navigator = navigator;
    this.settings = settings;
    this.video = video;
  }

  public Stage stage() {
    return stage;
  }

  public Scene scene() {
    return video.getScene();
  }

  public Pane canvas() {
    return video.getCanvas();
  }

  public InputState input() {
    return input;
  }

  public AssetRegistry assets() {
    return assets;
  }

  public AudioService audio() {
    return audio;
  }

  public ScreenManager navigator() {
    return navigator;
  }

  public GameSettings settings() {
    return settings;
  }

  public VideoManager video() {
    return video;
  }
}
