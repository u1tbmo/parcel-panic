package parcelpanic.logic;

import java.util.ArrayList;
import parcelpanic.shared.GameState;
import parcelpanic.shared.PlayerIntent;
import parcelpanic.world.TileMap;

// Handles the game logic and state updates for Milestone 1.
public final class GameSimulation {
  private double matchTimer;
  private final TileMap tileMap;

  public GameSimulation(TileMap tileMap) {
    this.tileMap = tileMap;
    this.matchTimer = 180.0; // Default match duration
  }

  // Updates the simulation state based on player intent and elapsed time.
  public void fixedUpdate(double dt, PlayerIntent intent) {
    matchTimer -= dt;
    if (matchTimer < 0) {
      matchTimer = 0;
    }
    // TODO: Implement vehicle movement and parcel logic for future milestones
  }

  // Returns an immutable snapshot of the current game state.
  public GameState getCurrentState() {
    return new GameState(
        matchTimer,
        0.0, // Unhappiness
        new ArrayList<>(), // Vehicles
        new ArrayList<>(), // Parcels
        tileMap
    );
  }

  public double getMatchTimer() {
    return matchTimer;
  }
}
