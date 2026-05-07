package parcelpanic.media;

public final class AssetKeys {
  private AssetKeys() {}

  /// Enum of Colors
  public enum ColorKey {
    PRIMARY("#2196F3"),
    SUCCESS("#4CAF50"),
    DANGER("#F44336"),
    WARNING("#FF9800"),
    TEXT("#212121"),
    TEXT_LIGHT("#FFFFFF"),
    TEXT_MUTED("#888888"),
    TEXT_DISABLED("#555555"),
    TEXT_HINT("#666666"),
    SURFACE_DARK("#1a1a1a"),
    SURFACE_BLACK("#000000"),
    BACKGROUND("#FAFAFA"),
    BORDER("#E0E0E0");

    private final String hex;

    ColorKey(String hex) {
      this.hex = hex;
    }

    public String getHex() {
      return hex;
    }
  }

  /// Enum of Images
  public enum ImageKey {
  // Define images here as needed
  // Example: LOGO("ui/logo.png")
    TILE_ROAD("world/road.png"),
    TILE_WALL("world/wall.png"),
    TILE_GRASS("world/grass.png"),
    TILE_HUB("world/hub.png");

    private final String path;

    ImageKey(String path) {
      this.path = path;
    }

    public String getPath() {
      return path;
    }
  }

  /// Enum of Audio Effects
  public enum AudioKey {
    CLICK("sfx/click_001"),
    MOVE_SELECTION("sfx/bong_001");

    private final String fileName;

    AudioKey(String fileName) {
      this.fileName = fileName;
    }

    public String getFileName() {
      return fileName;
    }
  }

  /// Enum of Fonts
  public enum FontKey {
    DISPLAY("RadioCanadaBig-Bold", 96),
    HEADLINE("RadioCanadaBig-Bold", 48),
    TITLE("RadioCanadaBig-Medium", 36),
    BODY("RadioCanadaBig-Regular", 16),
    LABEL("RadioCanadaBig-Medium", 24),
    HINT("KenneyInputKeyboardMouse", 48);

    private final String family;
    private final double defaultSize;

    FontKey(String family, double defaultSize) {
      this.family = family;
      this.defaultSize = defaultSize;
    }

    public String getFamily() {
      return family;
    }

    public double getDefaultSize() {
      return defaultSize;
    }

    public String getResourceName(double size) {
      return family + "-" + size;
    }
  }
}
