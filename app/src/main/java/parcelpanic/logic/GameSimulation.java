package parcelpanic.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import parcelpanic.logic.entities.ParcelLogic;
import parcelpanic.logic.entities.VehicleLogic;
import parcelpanic.shared.GameState;
import parcelpanic.shared.ParcelState;
import parcelpanic.shared.PlayerIntent;
import parcelpanic.shared.VehicleState;
import parcelpanic.world.TileMap;

public class GameSimulation {
  private final Map<Integer, VehicleLogic> vehicles = new HashMap<>();
  private final List<ParcelLogic> parcels = new ArrayList<>();

  private final Map<Integer, Double> interactionGauges = new HashMap<>();

  private double matchTimer = MatchRules.MATCH_DURATION;
  private double unhappiness = 0.0;
  private double score = 0.0;

  private final TileMap tileMap;

  public GameSimulation(TileMap tileMap) {
    this.tileMap = tileMap;
  }

  public void addPlayer(int id, double x, double y) {
    vehicles.put(id, new VehicleLogic(id, x, y));
    interactionGauges.put(id, 0.0);
  }

  public void addParcel(ParcelLogic parcel) {
    parcels.add(parcel);
  }

  public GameState update(double dt, List<PlayerIntent> intents) {
    matchTimer = Math.max(0, matchTimer - dt);

    if (MatchRules.isGameOver(unhappiness, matchTimer)) {
      // Logic for game over could be handled here or by the screen checking the state
    }

    for (PlayerIntent intent : intents) {
      VehicleLogic vehicle = vehicles.get(intent.playerId());
      if (vehicle == null) continue;

      double oldX = vehicle.x();
      double oldY = vehicle.y();

      vehicle.update(dt, intent);
      CollisionEngine.resolve(vehicle, tileMap, oldX, oldY);
      handleInteraction(dt, vehicle, intent);
      handleThrowing(vehicle, intent);
    }

    for (ParcelLogic parcel : parcels) {
      if (parcel.update(dt)) {
        unhappiness = MatchRules.calculatePenalty(unhappiness);
      }

      if (parcel.currentState() == ParcelLogic.State.CARRIED) {
        VehicleLogic carrier = vehicles.get(parcel.carrierId());
        if (carrier != null) {
          parcel.updatePosition(carrier.x(), carrier.y());
        }
      }

      if (parcel.currentState() == ParcelLogic.State.THROWN) {
        checkCatch(parcel);
      }
    }

    return generateSnapshot();
  }

  private void handleInteraction(double dt, VehicleLogic vehicle, PlayerIntent intent) {
    if (intent.interact()) {
      // 1. Check for Pickup (Hub or Ground)
      ParcelLogic nearby = findNearbyParcel(vehicle);
      if (nearby != null) {
        double progress = interactionGauges.get(vehicle.id()) + dt;
        if (progress >= MatchRules.INTERACT_TIME_REQUIRED) {
          nearby.pickup(vehicle.id());
          interactionGauges.put(vehicle.id(), 0.0);
        } else {
          interactionGauges.put(vehicle.id(), progress);
        }
        return; // Prioritize pickup over delivery if both are possible
      }

      // 2. Check for Delivery (At a House/Target Zone)
      int tx = (int) (vehicle.x() / MatchRules.TILE_SIZE);
      int ty = (int) (vehicle.y() / MatchRules.TILE_SIZE);

      if (tx >= 0 && tx < tileMap.getWidth() && ty >= 0 && ty < tileMap.getHeight()) {
        if (tileMap.getTile(tx, ty) == TileMap.TileType.TARGET_ZONE) {
          for (ParcelLogic p : parcels) {
            if (p.carrierId() != null && p.carrierId() == vehicle.id()) {
              score += MatchRules.calculateDeliveryScore(p.remainingTime());
              parcels.remove(p); // Remove delivered parcel
              break;
            }
          }
        }
      }
    } else {
      interactionGauges.put(vehicle.id(), 0.0);
    }
  }

  private void handleThrowing(VehicleLogic vehicle, PlayerIntent intent) {
    if (intent.throwParcel()) {
      for (ParcelLogic p : parcels) {
        if (p.carrierId() != null && p.carrierId() == vehicle.id()) {
          double throwSpeed = MatchRules.THROW_SPEED;
          double rad = Math.toRadians(vehicle.state().rotation());
          double vx = Math.sin(rad) * throwSpeed;
          double vy = -Math.cos(rad) * throwSpeed;
          p.launch(vehicle.x(), vehicle.y(), vx, vy);
          break;
        }
      }
    }
  }

  private void checkCatch(ParcelLogic parcel) {
    for (VehicleLogic vehicle : vehicles.values()) {
      double dist = Math.hypot(parcel.x() - vehicle.x(), parcel.y() - vehicle.y());
      if (dist < MatchRules.INTERACT_RANGE) {
        parcel.pickup(vehicle.id());
        return;
      }
    }
  }

  private ParcelLogic findNearbyParcel(VehicleLogic vehicle) {
    for (ParcelLogic p : parcels) {
      if (p.currentState() == ParcelLogic.State.ON_GROUND
          || p.currentState() == ParcelLogic.State.AT_HUB) {
        double dist = Math.hypot(p.x() - vehicle.x(), p.y() - vehicle.y());
        if (dist < MatchRules.INTERACT_RANGE) return p;
      }
    }
    return null;
  }

  public GameState generateSnapshot() {
    List<VehicleState> vStates = vehicles.values().stream().map(VehicleLogic::state).toList();
    List<ParcelState> pStates = parcels.stream().map(ParcelLogic::state).toList();
    return new GameState(matchTimer, unhappiness, score, vStates, pStates, tileMap);
  }

  public double getMatchTimer() {
    return matchTimer;
  }
}
