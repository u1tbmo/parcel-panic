package parcelpanic.settings;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
import javafx.scene.input.KeyCode;
import parcelpanic.input.InputAction;
import parcelpanic.video.DisplayMode;
import parcelpanic.video.Resolution;

public final class GameSettings {
  private static final Preferences prefs = Preferences.userNodeForPackage(GameSettings.class);

  private final VideoSettings video;
  private final AudioSettings audio;
  private final ControlsSettings controls;
  private static final String KEY_PLAYER_NAME = "player.name";
  private static final String KEY_COLOR_INDEX = "player.colorIndex";
  private String playerName = "Player";
  private int colorIndex = -1;

  public GameSettings() {
    this.video = new VideoSettings();
    this.audio = new AudioSettings();
    this.controls = new ControlsSettings();
  }

  public VideoSettings video() {
    return video;
  }

  public AudioSettings audio() {
    return audio;
  }

  public ControlsSettings controls() {
    return controls;
  }

  public String playerName() {
    return playerName;
  }

  public void setPlayerName(String name) {
    this.playerName = name != null && !name.trim().isEmpty() ? name.trim() : "Player";
  }

  public int colorIndex() {
    return colorIndex;
  }

  public void setColorIndex(int index) {
    this.colorIndex = index;
  }

  /// Load settings from persistent storage
  public void load() {
    video.load();
    audio.load();
    controls.load();
    this.playerName = prefs.get(KEY_PLAYER_NAME, "Player");
    this.colorIndex = prefs.getInt(KEY_COLOR_INDEX, -1);
  }

  /// Save settings to persistent storage
  public void save() {
    video.save();
    audio.save();
    controls.save();
    prefs.put(KEY_PLAYER_NAME, playerName);
    if (colorIndex >= 0) {
      prefs.putInt(KEY_COLOR_INDEX, colorIndex);
    }
  }

  public static class AudioSettings {
    private static final String KEY_MASTER_VOLUME = "audio.masterVolume";

    private double masterVolume;

    public AudioSettings() {
      this.masterVolume = 0.5;
    }

    public double masterVolume() {
      return masterVolume;
    }

    public void setMasterVolume(double volume) {
      this.masterVolume = Math.max(0.0, Math.min(1.0, volume));
    }

    void load() {
      this.masterVolume = prefs.getDouble(KEY_MASTER_VOLUME, 0.5);
    }

    void save() {
      prefs.putDouble(KEY_MASTER_VOLUME, masterVolume);
    }
  }

  public static class VideoSettings {
    private static final String KEY_WIDTH = "video.resolution.width";
    private static final String KEY_HEIGHT = "video.resolution.height";
    private static final String KEY_DISPLAY_MODE = "video.displayMode";

    private Resolution resolution;
    private DisplayMode displayMode;

    public VideoSettings() {
      // Default settings
      this.resolution = Resolution.RES_1280x720;
      this.displayMode = DisplayMode.WINDOWED;
    }

    public Resolution resolution() {
      return resolution;
    }

    public void setResolution(Resolution resolution) {
      this.resolution = resolution;
    }

    public DisplayMode displayMode() {
      return displayMode;
    }

    public void setDisplayMode(DisplayMode displayMode) {
      this.displayMode = displayMode;
    }

    void load() {
      int width = prefs.getInt(KEY_WIDTH, 1280);
      int height = prefs.getInt(KEY_HEIGHT, 720);
      this.resolution = Resolution.findPreset(width, height);
      String modeName = prefs.get(KEY_DISPLAY_MODE, "WINDOWED");
      try {
        this.displayMode = DisplayMode.valueOf(modeName);
      } catch (IllegalArgumentException e) {
        this.displayMode = DisplayMode.WINDOWED;
      }
    }

    void save() {
      prefs.putInt(KEY_WIDTH, resolution.width());
      prefs.putInt(KEY_HEIGHT, resolution.height());
      prefs.put(KEY_DISPLAY_MODE, displayMode.name());
    }
  }

  public static class ControlsSettings {
    private static final String PREFIX = "controls.action.";
    private final Map<KeyCode, List<InputAction>> keyToActions = new java.util.LinkedHashMap<>();

    public ControlsSettings() {
      resetToDefaults();
    }

    public List<InputAction> getActionsForKey(KeyCode code) {
      return keyToActions.getOrDefault(code, java.util.Collections.emptyList());
    }

    public void rebind(KeyCode code, InputAction action) {
      if (action == null) return;
      List<InputAction> actions = keyToActions.computeIfAbsent(code, _ -> new ArrayList<>());
      if (!actions.contains(action)) {
        actions.add(action);
      }
    }

    public void unbindAll(InputAction action) {
      for (List<InputAction> actions : keyToActions.values()) {
        actions.removeIf(a -> a == action);
      }
    }

    public List<KeyCode> getKeysForAction(InputAction action) {
      List<KeyCode> keys = new ArrayList<>();
      for (Map.Entry<KeyCode, List<InputAction>> entry : keyToActions.entrySet()) {
        if (entry.getValue().contains(action)) {
          keys.add(entry.getKey());
        }
      }
      return keys;
    }

    public void resetToDefaults() {
      keyToActions.clear();

      // UI / Interface
      rebind(KeyCode.UP, InputAction.UI_UP);
      rebind(KeyCode.DOWN, InputAction.UI_DOWN);
      rebind(KeyCode.LEFT, InputAction.UI_LEFT);
      rebind(KeyCode.RIGHT, InputAction.UI_RIGHT);
      rebind(KeyCode.C, InputAction.CONFIRM);
      rebind(KeyCode.X, InputAction.BACK);
      rebind(KeyCode.ESCAPE, InputAction.PAUSE);

      // Gameplay
      rebind(KeyCode.UP, InputAction.MOVE_UP);
      rebind(KeyCode.DOWN, InputAction.MOVE_DOWN);
      rebind(KeyCode.LEFT, InputAction.MOVE_LEFT);
      rebind(KeyCode.RIGHT, InputAction.MOVE_RIGHT);
      rebind(KeyCode.C, InputAction.INTERACT);
      rebind(KeyCode.X, InputAction.DASH);
      rebind(KeyCode.Z, InputAction.THROW);
    }

    void load() {
      for (InputAction action : InputAction.values()) {
        String val = prefs.get(PREFIX + action.name(), null);
        if (val != null) {
          unbindAll(action);
          String[] codes = val.split(",");
          for (String codeStr : codes) {
            try {
              KeyCode code = KeyCode.valueOf(codeStr.trim());
              rebind(code, action);
            } catch (IllegalArgumentException ignored) {
            }
          }
        }
      }
    }

    void save() {
      Map<InputAction, List<String>> actionToKeys = new EnumMap<>(InputAction.class);
      for (InputAction action : InputAction.values()) {
        actionToKeys.put(action, new ArrayList<>());
      }

      for (Map.Entry<KeyCode, List<InputAction>> entry : keyToActions.entrySet()) {
        for (InputAction action : entry.getValue()) {
          actionToKeys.get(action).add(entry.getKey().name());
        }
      }

      for (Map.Entry<InputAction, List<String>> entry : actionToKeys.entrySet()) {
        String val = String.join(",", entry.getValue());
        prefs.put(PREFIX + entry.getKey().name(), val);
      }
    }
  }
}
