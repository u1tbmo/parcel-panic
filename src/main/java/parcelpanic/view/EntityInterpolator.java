package parcelpanic.view;

/**
 * Smoothly interpolates an entity's render position.
 * Prevents visual snapping between network updates.
 */
public final class EntityInterpolator {
  private double previousX;
  private double previousY;

  private double targetX;
  private double targetY;

  private double renderX;
  private double renderY;

  private double elapsed;
  private final double duration;

  public EntityInterpolator(double startX, double startY, double durationMillis) {
    this.previousX = startX;
    this.previousY = startY;

    this.targetX = startX;
    this.targetY = startY;

    this.renderX = startX;
    this.renderY = startY;

    this.duration = durationMillis / 1000.0;
    this.elapsed = this.duration;
  }

  /** Called whenever a new authoritative position arrives. */
  public void setTarget(double newX, double newY) {
    if (targetX == newX && targetY == newY) {
      return;
    }

    this.previousX = renderX;
    this.previousY = renderY;

    this.targetX = newX;
    this.targetY = newY;

    this.elapsed = 0.0;
  }

  /** Updates interpolation progress. */
  public void update(double dt) {
    if (elapsed < duration) {
      elapsed += dt;

      double alpha = Math.min(elapsed / duration, 1.0);

      renderX = lerp(previousX, targetX, alpha);
      renderY = lerp(previousY, targetY, alpha);
    } else {
      renderX = targetX;
      renderY = targetY;
    }
  }

  private double lerp(double a, double b, double t) {
    return a + (b - a) * t;
  }

  public double getRenderX() {
    return renderX;
  }

  public double getRenderY() {
    return renderY;
  }
}