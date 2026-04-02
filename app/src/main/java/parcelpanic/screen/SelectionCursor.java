package parcelpanic.screen;

public final class SelectionCursor {
  private SelectionCursor() {}

  public static int wrapIndex(int currentIndex, int delta, int length) {
    return (currentIndex + delta + length) % length;
  }

  public static <T> T move(T[] options, T current, int delta) {
    int currentIndex = 0;
    for (int i = 0; i < options.length; i++) {
      if (options[i] == current) {
        currentIndex = i;
        break;
      }
    }
    return options[wrapIndex(currentIndex, delta, options.length)];
  }
}
