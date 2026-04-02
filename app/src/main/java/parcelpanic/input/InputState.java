package parcelpanic.input;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/// Holds and tracks the logical state of received inputs (Ticks)
public final class InputState {
  /// Map of `InputActions` that are currently active (held down) in the real-time tick
  private final Map<InputAction, Boolean> currentTick = new EnumMap<>(InputAction.class);
  /// Map of `InputActions` that were active at the start of the current tick
  private final Map<InputAction, Boolean> lastTick = new EnumMap<>(InputAction.class);
  /// Set of `InputActions` that were just pressed during this simulation step
  private final Set<InputAction> pressedThisTick = EnumSet.noneOf(InputAction.class);
  /// Set of `InputActions` that were just released during this simulation step
  private final Set<InputAction> releasedThisTick = EnumSet.noneOf(InputAction.class);
  /// Track how long each action has been held (in seconds)
  private final Map<InputAction, Double> heldTime = new EnumMap<>(InputAction.class);
  /// Track the last repeat time to handle the repeat rate
  private final Map<InputAction, Double> lastRepeatTime = new EnumMap<>(InputAction.class);

  private static final double INITIAL_REPEAT_DELAY = 0.4;
  private static final double REPEAT_RATE = 0.1;

  public InputState() {
    // Initialize all actions to not pressed
    for (InputAction action : InputAction.values()) {
      currentTick.put(action, false);
      lastTick.put(action, false);
      heldTime.put(action, 0.0);
      lastRepeatTime.put(action, 0.0);
    }
  }

  /// Sets an action as active for the current tick
  public void onActionPressed(InputAction action) {
    if (action != null && !currentTick.get(action)) {
      currentTick.put(action, true);
      pressedThisTick.add(action);
    }
  }

  /// Sets an action as inactive for the current tick
  public void onActionReleased(InputAction action) {
    if (action != null && currentTick.get(action)) {
      currentTick.put(action, false);
      releasedThisTick.add(action);
    }
  }

  /// Whether an action is held down in the current tick
  public boolean isPressed(InputAction action) {
    return currentTick.get(action);
  }

  /// Whether an action was just pressed this tick
  public boolean wasPressed(InputAction action) {
    return pressedThisTick.contains(action);
  }

  /// Whether an action was just released this tick
  public boolean wasReleased(InputAction action) {
    return releasedThisTick.contains(action);
  }

  /// Whether an action was held at the start of this tick
  public boolean wasHeldLastTick(InputAction action) {
    return lastTick.get(action);
  }

  /// Returns how long an action has been held (in seconds)
  public double getHeldTime(InputAction action) {
    return heldTime.getOrDefault(action, 0.0);
  }

  /// Updates held timers. Should be called before check logic in the loop.
  public void updateTimers(double dt) {
    for (InputAction action : InputAction.values()) {
      if (currentTick.get(action)) {
        heldTime.put(action, heldTime.get(action) + dt);
      } else {
        heldTime.put(action, 0.0);
        lastRepeatTime.put(action, 0.0);
      }
    }
  }

  /// Checks if an action should trigger a repeat event this tick
  public boolean shouldRepeat(InputAction action) {
    double time = heldTime.get(action);
    if (time < INITIAL_REPEAT_DELAY) {
      return false;
    }

    double sinceLastRepeat = time - lastRepeatTime.get(action);
    if (sinceLastRepeat >= REPEAT_RATE) {
      lastRepeatTime.put(action, time);
      return true;
    }

    return false;
  }

  /// Updates the states to prepare for the next tick.
  /// Called at the end of every simulation step by the loop.
  public void endOfTick() {
    lastTick.putAll(currentTick);
    pressedThisTick.clear();
    releasedThisTick.clear();
  }
}
