package parcelpanic.shared;

import java.util.List;
import parcelpanic.world.TileMap;

/// The complete, immutable snapshot of the game world at a specific moment in time.
/// Produced by the Simulator and read by the Renderer.
public record GameState(
    double matchTimer, // Time remaining in the round
    double unhappiness, // Percentage from 0.0 to 1.0 (or 0 to 100)
    double score, // Total cash earned from deliveries
    List<VehicleState> vehicles,
    List<ParcelState> parcels,
    TileMap map) {}
