package parcelpanic.video;

public enum DisplayMode {
  WINDOWED("Windowed"),
  BORDERLESS("Borderless Fullscreen");

  private final String displayName;

  DisplayMode(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }

  @Override
  public String toString() {
    return displayName;
  }
}
