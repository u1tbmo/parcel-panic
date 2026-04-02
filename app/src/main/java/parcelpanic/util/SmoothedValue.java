package parcelpanic.util;

/// A utility for values that need to be smoothly animated and interpolated within a fixed-timestep
// loop.
public final class SmoothedValue {
  private double current;
  private double previous;
  private final double speed;

  public SmoothedValue(double initial, double speed) {
    this.current = initial;
    this.previous = initial;
    this.speed = speed;
  }

  /// Updates the value toward a target. Should be called in fixedUpdate().
  public void update(double target) {
    this.previous = this.current;
    this.current += (target - this.current) * this.speed;
  }

  /// Returns the interpolated value for the current frame. Should be called in render().
  public double get(double alpha) {
    return previous + (current - previous) * alpha;
  }

  public double getCurrent() {
    return current;
  }
}
