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
    TILE_ROAD("tiles/tile_0468.png"),
    TILE_WALL("tiles/tile_0028.png"),
    TILE_HUB("tiles/tile_0012.png"),
    TILE_TARGET("tiles/tile_0455.png"),
    VEHICLE_CAR("entities/red_1.png"),
    VEHICLE_CAR_RED_1("entities/red_1.png"),
    VEHICLE_CAR_RED_2("entities/red_2.png"),
    VEHICLE_CAR_BLUE_1("entities/blue_1.png"),
    VEHICLE_CAR_BLUE_2("entities/blue_2.png"),
    VEHICLE_CAR_GREEN_1("entities/green_1.png"),
    VEHICLE_CAR_GREEN_2("entities/green_2.png"),
    VEHICLE_CAR_YELLOW_1("entities/yellow_1.png"),
    VEHICLE_CAR_YELLOW_2("entities/yellow_2.png"),
    VEHICLE_CAR_ORANGE_1("entities/orange_1.png"),
    VEHICLE_CAR_ORANGE_2("entities/orange_2.png"),
    VEHICLE_CAR_PINK_1("entities/pink_1.png"),
    VEHICLE_CAR_PINK_2("entities/pink_2.png"),
    VEHICLE_CAR_MAGENTA_1("entities/magenta_1.png"),
    VEHICLE_CAR_MAGENTA_2("entities/magenta_2.png"),


    MAP_LAYER_GRASS("map_renders/1_outergrass.png"),
    MAP_LAYER_BUILDINGS("map_renders/2_buildings.png"),
    MAP_LAYER_OBSTACLES("map_renders/3_obstacles.png"),
    MAP_LAYER_TARGET("map_renders/target_call.png"),

    MENU_MAIN("menu/title.gif"),

    ENTITY_PARCEL("entities/parcel.png"),
    EMOTE_CROSS("emotes/emote_cross.png");
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
