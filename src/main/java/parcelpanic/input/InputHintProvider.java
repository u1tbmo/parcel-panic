package parcelpanic.input;

import java.util.EnumMap;
import java.util.Map;
import javafx.scene.input.KeyCode;
import parcelpanic.settings.GameSettings;

/// Provider for input hint icons for each keycode
public final class InputHintProvider {
  private static final Map<KeyCode, String> KEY_TO_ICON = new EnumMap<>(KeyCode.class);

  static {
    // Arrows
    KEY_TO_ICON.put(KeyCode.UP, "\uE023");
    KEY_TO_ICON.put(KeyCode.DOWN, "\uE01D");
    KEY_TO_ICON.put(KeyCode.LEFT, "\uE01F");
    KEY_TO_ICON.put(KeyCode.RIGHT, "\uE021");

    // Digits
    KEY_TO_ICON.put(KeyCode.DIGIT0, "\uE001");
    KEY_TO_ICON.put(KeyCode.DIGIT1, "\uE003");
    KEY_TO_ICON.put(KeyCode.DIGIT2, "\uE005");
    KEY_TO_ICON.put(KeyCode.DIGIT3, "\uE007");
    KEY_TO_ICON.put(KeyCode.DIGIT4, "\uE009");
    KEY_TO_ICON.put(KeyCode.DIGIT5, "\uE00B");
    KEY_TO_ICON.put(KeyCode.DIGIT6, "\uE00D");
    KEY_TO_ICON.put(KeyCode.DIGIT7, "\uE00F");
    KEY_TO_ICON.put(KeyCode.DIGIT8, "\uE011");
    KEY_TO_ICON.put(KeyCode.DIGIT9, "\uE013");

    // Letters
    KEY_TO_ICON.put(KeyCode.A, "\uE015");
    KEY_TO_ICON.put(KeyCode.B, "\uE036");
    KEY_TO_ICON.put(KeyCode.C, "\uE046");
    KEY_TO_ICON.put(KeyCode.D, "\uE056");
    KEY_TO_ICON.put(KeyCode.E, "\uE05A");
    KEY_TO_ICON.put(KeyCode.F, "\uE066");
    KEY_TO_ICON.put(KeyCode.G, "\uE082");
    KEY_TO_ICON.put(KeyCode.H, "\uE084");
    KEY_TO_ICON.put(KeyCode.I, "\uE088");
    KEY_TO_ICON.put(KeyCode.J, "\uE08C");
    KEY_TO_ICON.put(KeyCode.K, "\uE08E");
    KEY_TO_ICON.put(KeyCode.L, "\uE090");
    KEY_TO_ICON.put(KeyCode.M, "\uE092");
    KEY_TO_ICON.put(KeyCode.N, "\uE096");
    KEY_TO_ICON.put(KeyCode.O, "\uE09E");
    KEY_TO_ICON.put(KeyCode.P, "\uE0A3");
    KEY_TO_ICON.put(KeyCode.Q, "\uE0AF");
    KEY_TO_ICON.put(KeyCode.R, "\uE0B5");
    KEY_TO_ICON.put(KeyCode.S, "\uE0B9");
    KEY_TO_ICON.put(KeyCode.T, "\uE0C9");
    KEY_TO_ICON.put(KeyCode.U, "\uE0D3");
    KEY_TO_ICON.put(KeyCode.V, "\uE0D5");
    KEY_TO_ICON.put(KeyCode.W, "\uE0D7");
    KEY_TO_ICON.put(KeyCode.X, "\uE0DB");
    KEY_TO_ICON.put(KeyCode.Y, "\uE0DD");
    KEY_TO_ICON.put(KeyCode.Z, "\uE0DF");

    // Special Keys
    KEY_TO_ICON.put(KeyCode.ENTER, "\uE05E");
    KEY_TO_ICON.put(KeyCode.ESCAPE, "\uE062");
    KEY_TO_ICON.put(KeyCode.BACK_SPACE, "\uE038");
    KEY_TO_ICON.put(KeyCode.SPACE, "\uE0C5");
    KEY_TO_ICON.put(KeyCode.TAB, "\uE0CB");
    KEY_TO_ICON.put(KeyCode.CONTROL, "\uE054");
    KEY_TO_ICON.put(KeyCode.ALT, "\uE017");
    KEY_TO_ICON.put(KeyCode.SHIFT, "\uE0BD");
    KEY_TO_ICON.put(KeyCode.WINDOWS, "\uE0D9");
    KEY_TO_ICON.put(KeyCode.COMMAND, "\uE052");
    KEY_TO_ICON.put(KeyCode.META, "\uE052");
    KEY_TO_ICON.put(KeyCode.CAPS, "\uE048");
    KEY_TO_ICON.put(KeyCode.NUM_LOCK, "\uE098");
    KEY_TO_ICON.put(KeyCode.PRINTSCREEN, "\uE0AD");
    KEY_TO_ICON.put(KeyCode.INSERT, "\uE08A");
    KEY_TO_ICON.put(KeyCode.DELETE, "\uE058");
    KEY_TO_ICON.put(KeyCode.HOME, "\uE086");
    KEY_TO_ICON.put(KeyCode.END, "\uE05C");
    KEY_TO_ICON.put(KeyCode.PAGE_UP, "\uE0A7");
    KEY_TO_ICON.put(KeyCode.PAGE_DOWN, "\uE0A5");

    // Punctuation & Symbols
    KEY_TO_ICON.put(KeyCode.MINUS, "\uE094");
    KEY_TO_ICON.put(KeyCode.PLUS, "\uE0AB");
    KEY_TO_ICON.put(KeyCode.EQUALS, "\uE060");
    KEY_TO_ICON.put(KeyCode.ASTERISK, "\uE034");
    KEY_TO_ICON.put(KeyCode.OPEN_BRACKET, "\uE044");
    KEY_TO_ICON.put(KeyCode.CLOSE_BRACKET, "\uE03E");
    KEY_TO_ICON.put(KeyCode.SEMICOLON, "\uE0BB");
    KEY_TO_ICON.put(KeyCode.COLON, "\uE04E");
    KEY_TO_ICON.put(KeyCode.COMMA, "\uE050");
    KEY_TO_ICON.put(KeyCode.PERIOD, "\uE0A9");
    KEY_TO_ICON.put(KeyCode.BACK_SLASH, "\uE0C1");
    KEY_TO_ICON.put(KeyCode.SLASH, "\uE0C3");
    KEY_TO_ICON.put(KeyCode.QUOTE, "\uE01B");
    KEY_TO_ICON.put(KeyCode.BACK_QUOTE, "\uE0B3");

    // Function Keys
    KEY_TO_ICON.put(KeyCode.F1, "\uE067");
    KEY_TO_ICON.put(KeyCode.F2, "\uE06F");
    KEY_TO_ICON.put(KeyCode.F3, "\uE071");
    KEY_TO_ICON.put(KeyCode.F4, "\uE073");
    KEY_TO_ICON.put(KeyCode.F5, "\uE075");
    KEY_TO_ICON.put(KeyCode.F6, "\uE077");
    KEY_TO_ICON.put(KeyCode.F7, "\uE079");
    KEY_TO_ICON.put(KeyCode.F8, "\uE07B");
    KEY_TO_ICON.put(KeyCode.F9, "\uE07D");
    KEY_TO_ICON.put(KeyCode.F10, "\uE068");
    KEY_TO_ICON.put(KeyCode.F11, "\uE06A");
    KEY_TO_ICON.put(KeyCode.F12, "\uE06C");
  }

  private InputHintProvider() {}

  public static String getIcon(KeyCode code) {
    return KEY_TO_ICON.getOrDefault(code, code.getName());
  }

  public static String getIconForAction(
      InputAction action, GameSettings.ControlsSettings settings) {
    var keys = settings.getKeysForAction(action);
    if (keys.isEmpty()) return "?";
    // Prefer arrows or letters over others if possible
    return getIcon(keys.get(0));
  }
}
