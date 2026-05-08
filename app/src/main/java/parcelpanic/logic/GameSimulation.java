package parcelpanic.logic;

import java.util.List;
import parcelpanic.shared.GameState;
import parcelpanic.shared.ParcelState;
import parcelpanic.shared.PlayerIntent;
import parcelpanic.shared.VehicleState;
import parcelpanic.world.TileMap;

// Handles the game logic and state updates for Milestone 1.
public final class GameSimulation {
  private double matchTimer;
  private final TileMap tileMap;

  private double vehicleX;
  private double vehicleY;
  private double vehicleRotation;

  public GameSimulation(TileMap tileMap) {
    this.tileMap = tileMap;
    this.matchTimer = 180.0; // Default match duration

    this.vehicleX = 300;
    this.vehicleY = 300;
    this.vehicleRotation = 0;
  }

  // Updates the simulation state based on player intent and elapsed time.
  public void fixedUpdate(double dt, PlayerIntent intent) {
    matchTimer -= dt;
    if (matchTimer < 0) {
      matchTimer = 0;
    }

    // Temporary Milestone 1 movement logic for testing the renderer.
    double speed = 120.0;

    if (intent.up()) {
      vehicleY -= speed * dt;
      vehicleRotation = 270;
    }
    if (intent.down()) {
      vehicleY += speed * dt;
      vehicleRotation = 90;
    }
    if (intent.left()) {
      vehicleX -= speed * dt;
      vehicleRotation = 180;
    }
    if (intent.right()) {
      vehicleX += speed * dt;
      vehicleRotation = 0;
    }
  }

  // Returns an immutable snapshot of the current game state.
  public GameState getCurrentState() {
    return new GameState(
        matchTimer,
        0.0, // Unhappiness
        List.of(new VehicleState(1, vehicleX, vehicleY, vehicleRotation, false)), // Vehicles
        List.of(new ParcelState(1, 500, 300, null, 60, false, 1)), // Parcels
        tileMap);
  }

  public double getMatchTimer() {
    return matchTimer;
  }
}