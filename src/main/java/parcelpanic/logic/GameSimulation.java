package parcelpanic.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javafx.geometry.Point2D;
import parcelpanic.logic.entities.ParcelLogic;
import parcelpanic.logic.entities.VehicleLogic;
import parcelpanic.shared.GameState;
import parcelpanic.shared.ParcelState;
import parcelpanic.shared.PlayerIntent;
import parcelpanic.shared.VehicleState;
import parcelpanic.shared.VehicleState.PromptType;
import parcelpanic.world.TileMap;

public class GameSimulation {
  private final Map<Integer, VehicleLogic> vehicles = new HashMap<>();
  private final List<ParcelLogic> parcels = new ArrayList<>();
  private final Random random = new Random();

  private final Map<Integer, Double> interactionGauges = new HashMap<>();

  private double matchTimer = MatchRules.MATCH_DURATION;
  private double unhappiness = 0.0;
  private double score = 0.0;
  private double spawnTimer = 2.0; // Initial delay before first spawn

  private final TileMap tileMap;
  private final List<Point2D> hubLocations;
  private final List<Point2D> targetLocations;
  private int parcelIdCounter = 1;

  public GameSimulation(TileMap tileMap) {
    this.tileMap = tileMap;
    this.hubLocations = tileMap.getTilesOfType(TileMap.TileType.HUB);
    this.targetLocations = tileMap.getTilesOfType(TileMap.TileType.TARGET_ZONE);
  }

  public void addPlayer(int id, double x, double y) {
    double spawnX = 0;
    double spawnY = 0;

    // spawn at hub
    if (hubLocations != null && !hubLocations.isEmpty()) {
      // pick a random HUB tile
      Point2D randomHub = hubLocations.get(random.nextInt(hubLocations.size()));

      spawnX = randomHub.getX() * MatchRules.TILE_SIZE + (MatchRules.TILE_SIZE / 2.0);
      spawnY = randomHub.getY() * MatchRules.TILE_SIZE + (MatchRules.TILE_SIZE / 2.0);
    }
    vehicles.put(id, new VehicleLogic(id, spawnX, spawnY));
    interactionGauges.put(id, 0.0);
  }

  public void setVehicleColor(int id, int colorIndex) {
    VehicleLogic v = vehicles.get(id);
    if (v != null) {
      v.setColorIndex(colorIndex);
    }
  }

  public void addParcel(ParcelLogic parcel) {
    parcels.add(parcel);
  }

  public GameState update(double dt, List<PlayerIntent> intents) {
    matchTimer = Math.max(0, matchTimer - dt);

    if (MatchRules.isGameOver(unhappiness, matchTimer)) {
      // Logic for game over
    }

    // Handle Parcel Spawning
    spawnTimer -= dt;
    if (spawnTimer <= 0) {
      spawnParcel();
      spawnTimer = MatchRules.PARCEL_SPAWN_INTERVAL;
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
      updatePrompt(vehicle);
    }

    for (int i = parcels.size() - 1; i >= 0; i--) {
      ParcelLogic parcel = parcels.get(i);
      double oldX = parcel.x();
      double oldY = parcel.y();

      if (parcel.update(dt)) {
        unhappiness = MatchRules.calculatePenalty(unhappiness);
        parcels.remove(i);
        continue;
      }

      if (parcel.currentState() == ParcelLogic.State.CARRIED) {
        VehicleLogic carrier = vehicles.get(parcel.carrierId());
        if (carrier != null) {
          parcel.updatePosition(carrier.x(), carrier.y());
        }
      }

      if (parcel.currentState() == ParcelLogic.State.THROWN) {
        CollisionEngine.resolveParcel(parcel, tileMap, oldX, oldY);
        checkCatch(parcel);
      }
    }

    return generateSnapshot();
  }

  private void spawnParcel() {
    if (parcels.size() >= MatchRules.MAX_PARCELS_ON_SCREEN) return;
    if (hubLocations.isEmpty() || targetLocations.isEmpty()) return;

    Point2D hub = hubLocations.get(random.nextInt(hubLocations.size()));
    int targetId = random.nextInt(targetLocations.size());

    // Center the parcel in the 40x40 tile
    double px = hub.getX() * MatchRules.TILE_SIZE + 20;
    double py = hub.getY() * MatchRules.TILE_SIZE + 20;

    parcels.add(new ParcelLogic(parcelIdCounter++, targetId, px, py));
  }

  private void handleInteraction(double dt, VehicleLogic vehicle, PlayerIntent intent) {
    if (intent.interact()) {
      // Check for Pickup (Hub or Ground)
      ParcelLogic nearby = findNearbyParcel(vehicle);
      if (nearby != null && vehicle.canPickup()) {
        double progress = interactionGauges.get(vehicle.id()) + dt;
        if (progress >= MatchRules.INTERACT_TIME_REQUIRED) {
          nearby.pickup(vehicle.id());
          interactionGauges.put(vehicle.id(), 0.0);
        } else {
          interactionGauges.put(vehicle.id(), progress);
        }
        return;
      }

      // Check for Delivery (At a House/Target Zone)
      int tx = (int) Math.floor(vehicle.x() / MatchRules.TILE_SIZE);
      int ty = (int) Math.floor(vehicle.y() / MatchRules.TILE_SIZE);

      if (tx >= 0 && tx < tileMap.getWidth() && ty >= 0 && ty < tileMap.getHeight()) {
        if (tileMap.getTile(tx, ty) == TileMap.TileType.TARGET_ZONE) {
          // Identify which target zone this tile represents
          int currentZoneId = -1;
          for (int i = 0; i < targetLocations.size(); i++) {
            Point2D loc = targetLocations.get(i);
            if ((int) loc.getX() == tx && (int) loc.getY() == ty) {
              currentZoneId = i;
              break;
            }
          }

          // Check if carried parcel matches this zone
          for (int i = parcels.size() - 1; i >= 0; i--) {
            ParcelLogic p = parcels.get(i);
            if (p.carrierId() != null && p.carrierId() == vehicle.id()) {
              if (p.targetHouseId() == currentZoneId) {
                score += MatchRules.calculateDeliveryScore(p.remainingTime());
                parcels.remove(i);
                break;
              }
            }
          }
        }
      }
    } else {
      interactionGauges.put(vehicle.id(), 0.0);
    }
  }

  private void updatePrompt(VehicleLogic vehicle) {
    ParcelLogic carried = findCarriedParcel(vehicle.id());
    if (carried != null) {
      int tx = (int) Math.floor(vehicle.x() / MatchRules.TILE_SIZE);
      int ty = (int) Math.floor(vehicle.y() / MatchRules.TILE_SIZE);
      if (tx < 0 || tx >= tileMap.getWidth() || ty < 0 || ty >= tileMap.getHeight()) {
        vehicle.setPrompt(PromptType.NONE);
        return;
      }

      if (tileMap.getTile(tx, ty) != TileMap.TileType.TARGET_ZONE) {
        vehicle.setPrompt(PromptType.NONE);
        return;
      }

      int currentZoneId = -1;
      for (int i = 0; i < targetLocations.size(); i++) {
        Point2D loc = targetLocations.get(i);
        if ((int) loc.getX() == tx && (int) loc.getY() == ty) {
          currentZoneId = i;
          break;
        }
      }

      if (currentZoneId < 0) {
        vehicle.setPrompt(PromptType.NONE);
        return;
      }

      vehicle.setPrompt(
          carried.targetHouseId() == currentZoneId
              ? PromptType.DELIVER_OK
              : PromptType.DELIVER_WRONG);
      return;
    }

    ParcelLogic nearby = findNearbyParcel(vehicle);
    if (nearby != null) {
      vehicle.setPrompt(PromptType.PICKUP);
      return;
    }

    vehicle.setPrompt(PromptType.NONE);
  }

  private ParcelLogic findCarriedParcel(int vehicleId) {
    for (ParcelLogic parcel : parcels) {
      if (parcel.carrierId() != null && parcel.carrierId() == vehicleId) {
        return parcel;
      }
    }
    return null;
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
          vehicle.triggerPickupCooldown();
          break;
        }
      }
    }
  }

  private void checkCatch(ParcelLogic parcel) {
    for (VehicleLogic vehicle : vehicles.values()) {
      // Check if vehicle is in cooldown
      if (!vehicle.canPickup()) continue;

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

  public void removePlayer(int id) {
    vehicles.remove(id);
    interactionGauges.remove(id);
    for (ParcelLogic p : parcels) {
      if (p.carrierId() != null && p.carrierId() == id) {
        p.drop();
      }
    }
  }
}
