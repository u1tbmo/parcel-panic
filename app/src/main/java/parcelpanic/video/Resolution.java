package parcelpanic.video;

import java.util.ArrayList;
import java.util.List;
import javafx.stage.Screen;

public record Resolution(int width, int height, String displayName) {
  public double getAspectRatio() {
    return (double) width() / height();
  }

  @Override
  public String toString() {
    return displayName();
  }

  // Common resolutions
  public static final Resolution RES_800x600 = new Resolution(800, 600, "800x600");
  public static final Resolution RES_1024x768 = new Resolution(1024, 768, "1024x768");
  public static final Resolution RES_1280x720 = new Resolution(1280, 720, "1280x720");
  public static final Resolution RES_1920x1080 = new Resolution(1920, 1080, "1920x1080");
  public static final Resolution RES_2560x1440 = new Resolution(2560, 1440, "2560x1440");
  public static final Resolution RES_3840x2160 = new Resolution(3840, 2160, "3840x2160");

  public static final Resolution[] ALL_PRESETS = {
    RES_800x600, RES_1024x768, RES_1280x720, RES_1920x1080, RES_2560x1440, RES_3840x2160,
  };

  /// Get available presets filtered by actual screen resolution
  public static Resolution[] getAvailablePresets() {
    // Get primary screen resolution (without scaling)
    Screen primary = Screen.getPrimary();
    double screenWidth = primary.getBounds().getWidth();
    double screenHeight = primary.getBounds().getHeight();

    // Filter presets that fit on the screen
    List<Resolution> available = new ArrayList<>();
    for (Resolution preset : ALL_PRESETS) {
      if (preset.width() <= screenWidth && preset.height() <= screenHeight) {
        available.add(preset);
      }
    }

    // Always include at least one resolution
    if (available.isEmpty()) {
      available.add(RES_800x600);
    }

    return available.toArray(new Resolution[0]);
  }

  public static Resolution findPreset(int width, int height) {
    for (Resolution preset : ALL_PRESETS) {
      if (preset.width() == width && preset.height() == height) {
        return preset;
      }
    }
    return new Resolution(width, height, width + "x" + height);
  }
}
