package parcelpanic.shared;

/// Represents the state of a single parcel in the world.
/// Produced by the Simulator and read by the Renderer.
public record ParcelState(
    int id,
    double x,
    double y,
    Integer carrierId, // The ID of the Vehicle carrying it, or null if it's on the ground/Hub
    double remainingTime, // Seconds left before the delivery expires
    boolean isDamaged,
    int targetHouseId) {}
