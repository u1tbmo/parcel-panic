package parcelpanic.input;

import parcelpanic.shared.PlayerIntent;

// Translates local keyboard input (from InputState) into a PlayerIntent.
public final class LocalPlayerController {

  // Creates a PlayerIntent based on the current state of the input actions.
  public PlayerIntent createIntent(int playerId, InputState input) {
    return new PlayerIntent(
        playerId,
        input.isPressed(InputAction.MOVE_UP),
        input.isPressed(InputAction.MOVE_DOWN),
        input.isPressed(InputAction.MOVE_LEFT),
        input.isPressed(InputAction.MOVE_RIGHT),
        input.isPressed(InputAction.DASH),
        input.isPressed(InputAction.INTERACT),
        input.isPressed(InputAction.THROW));
  }
}
