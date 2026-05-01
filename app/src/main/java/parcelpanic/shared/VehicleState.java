package parcelpanic.shared;

/// Represents the current physical and logical state of a vehicle.
/// Produced by the Simulator and read by the Renderer.
public record VehicleState(
    int id,
    double x,
    double y,
    double rotation, // Facing direction in degrees
    boolean isDashing
) {}
