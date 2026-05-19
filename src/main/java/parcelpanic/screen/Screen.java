package parcelpanic.screen;

import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import parcelpanic.input.InputAction;
import parcelpanic.runtime.AppContext;

/// Screen interface
public interface Screen {
  /// The root node of the screen's UI
  Node getRoot();

  /// Called once when the screen becomes active
  void enter(AppContext ctx);

  /// Called before screen is removed
  void exit();

  /// Called every simulation tick
  void fixedUpdate(double dtSeconds);

  /// Called every frame for interpolation
  void render(double alpha);

  /// Input routing
  default void onKeyPressed(InputAction action) {}

  default void onKeyReleased(InputAction action) {}

  /// Raw input routing
  default void onRawKeyPressed(KeyCode code) {}

  default void onRawKeyReleased(KeyCode code) {}

  /// Whether InputAction bindings should be suppressed for this screen
  default boolean suppressActionBindings() {
    return false;
  }

  /// Whether this screen supports automatic input repetition
  default boolean supportsInputRepeat() {
    return false;
  }
}
