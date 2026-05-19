package parcelpanic.loop;

import java.util.function.Consumer;
import javafx.animation.AnimationTimer;

/// A fixed time step loop
public final class FixedStepLoop extends AnimationTimer {
  /// The clamp value to prevent spiral of death
  private static final double MAX_DELTA = 0.25;

  /// The callback function to run on every tick
  private final Consumer<Double> onTick;

  /// The callback function to run on every render
  private final Consumer<Double> onRender;

  /// A fixed delta-time based on the ticks per second
  private final double fixedDt;

  /// The previous time value
  private long previousTime = -1L;

  /// The time that has not yet been processed
  private double accumulatedTime = 0.0;

  public FixedStepLoop(double ticksPerSecond, Consumer<Double> onTick, Consumer<Double> onRender) {
    this.onTick = onTick;
    this.onRender = onRender;
    this.fixedDt = 1.0 / ticksPerSecond;
  }

  @Override
  public void handle(long now) {
    // Set previous time on start
    if (previousTime < 0L) {
      previousTime = now;
      return;
    }

    // Calculate the difference in time from now to previously, clamping to MAX_DELTA
    double deltaTime = (now - previousTime) / 1_000_000_000.0;
    previousTime = now;
    if (deltaTime > MAX_DELTA) {
      deltaTime = MAX_DELTA;
    }

    // Add the delta time to the accumulator
    accumulatedTime += deltaTime;

    // Run onTick callback function per tick
    while (accumulatedTime >= fixedDt) {
      onTick.accept(fixedDt);
      accumulatedTime -= fixedDt;
    }

    // Calculate the interpolation factor
    double alpha = accumulatedTime / fixedDt;

    // Run onRender callback function per frame
    onRender.accept(alpha);
  }
}
