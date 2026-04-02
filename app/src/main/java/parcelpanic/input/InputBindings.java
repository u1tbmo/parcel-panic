package parcelpanic.input;

import java.util.List;
import javafx.scene.Scene;
import parcelpanic.screen.ScreenManager;
import parcelpanic.settings.GameSettings;

public final class InputBindings {
  private InputBindings() {}

  public static void bindScene(
      Scene scene,
      InputState input,
      ScreenManager screens,
      GameSettings.ControlsSettings settings) {
    scene.setOnKeyPressed(
        event -> {
          if (!screens.isInputSuppressed()) {
            List<InputAction> actions = settings.getActionsForKey(event.getCode());
            for (InputAction action : actions) {
              input.onActionPressed(action);
            }
          }
          screens.onRawKeyPressed(event.getCode());
        });

    scene.setOnKeyReleased(
        event -> {
          if (!screens.isInputSuppressed()) {
            List<InputAction> actions = settings.getActionsForKey(event.getCode());
            for (InputAction action : actions) {
              input.onActionReleased(action);
            }
          }
          screens.onRawKeyReleased(event.getCode());
        });
  }
}
