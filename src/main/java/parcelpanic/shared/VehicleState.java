package parcelpanic.shared;

/// Represents the current physical and logical state of a vehicle.
/// Produced by the Simulator and read by the Renderer.
public record VehicleState(
    int id,
    double x,
    double y,
    double vx,
    double vy,
    double rotation,
    boolean isDashing,
    boolean isAccelerating,
    PromptType prompt,
    int colorIndex) {
  public enum PromptType {
    NONE,
    PICKUP,
    DELIVER_OK,
    DELIVER_WRONG
  }
}
