package parcelpanic.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import javafx.geometry.Point2D;
import parcelpanic.logic.entities.ParcelLogic;
import parcelpanic.logic.entities.VehicleLogic;
import parcelpanic.media.AssetKeys.AudioKey;
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
  private final Map<Integer, Boolean> wasInteracting = new HashMap<>();

  private double matchTimer = MatchRules.MATCH_DURATION;
  private double unhappiness = 0.0;
  private double score = 0.0;
  private int deliveredCount = 0;
  private int expiredCount = 0;
  private double spawnTimer = 2.0; // Initial delay before first spawn

  private final TileMap tileMap;
  private final List<Point2D> hubLocations;
  private final List<Point2D> targetLocations;
  private int parcelIdCounter = 1;

  private final Consumer<AudioKey> playSound;
  private final List<AudioKey> pendingSounds = new ArrayList<>();

  public GameSimulation(TileMap tileMap, Consumer<AudioKey> playSound) {
    this.tileMap = tileMap;
    this.hubLocations = tileMap.getTilesOfType(TileMap.TileType.HUB);
    this.targetLocations = tileMap.getTilesOfType(TileMap.TileType.TARGET_ZONE);
    this.playSound = playSound != null ? playSound : (k -> {});
  }

  public void addPlayer(int id, double x, double y, String playerName) {
    double spawnX = 0;
    double spawnY = 0;

    // spawn at hub
    if (hubLocations != null && !hubLocations.isEmpty()) {
      // pick a random HUB tile
      Point2D randomHub = hubLocations.get(random.nextInt(hubLocations.size()));

      spawnX = randomHub.getX() * MatchRules.TILE_SIZE + (MatchRules.TILE_SIZE / 2.0);
      spawnY = randomHub.getY() * MatchRules.TILE_SIZE + (MatchRules.TILE_SIZE / 2.0);
    }
    VehicleLogic vehicle = new VehicleLogic(id, spawnX, spawnY);
    vehicle.setPlayerName(playerName);
    vehicles.put(id, vehicle);
    interactionGauges.put(id, 0.0);
    wasInteracting.put(id, false);
  }

  public void setVehicleColor(int id, int colorIndex) {
    VehicleLogic v = vehicles.get(id);
    if (v != null) {
      v.setColorIndex(colorIndex);
    }
  }

  public void setVehiclePlayerName(int id, String playerName) {
    VehicleLogic v = vehicles.get(id);
    if (v != null) {
      v.setPlayerName(playerName);
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

    // Handle Parcel Spawning - increase stress over time
    spawnTimer -= dt;
    if (spawnTimer <= 0) {
      // Batch size increases as match progresses (1 at start, up to 3 at end)
      double progress = 1.0 - (matchTimer / MatchRules.MATCH_DURATION);
      int batchSize = 1 + (int) (progress * 1.0); // 1 -> 2

      // Spawn interval decreases over time (7.5s -> 5.0s)
      double currentInterval = MatchRules.PARCEL_SPAWN_INTERVAL - (progress * 2.5);

      int spawned = 0;
      int maxSpawn = Math.min(batchSize, MatchRules.MAX_PARCELS_ON_SCREEN - parcels.size());

      for (int i = 0; i < maxSpawn; i++) {
        if (spawnParcelInternal()) {
          spawned++;
        }
      }

      if (spawned > 0) {
        spawnTimer = Math.max(5.0, currentInterval);
      } else {
        // No spawns happened (no hubs/targets), try again soon
        spawnTimer = 1.0;
      }
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
        expiredCount++;
        playSound.accept(AudioKey.PARCEL_EXPIRED);
        pendingSounds.add(AudioKey.PARCEL_EXPIRED);
        if (parcel.currentState() == ParcelLogic.State.CARRIED) {
          VehicleLogic carrier = vehicles.get(parcel.carrierId());
          if (carrier != null) {
            carrier.setHasParcel(false);
          }
        }
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

        // Parcels can cross certain tiles, but they can't come to rest on them.
        if (parcel.isStoppedOnGround()) {
          ensureParcelLandsOnValidTile(parcel);
        }
      }
    }

    return generateSnapshot();
  }

  private boolean spawnParcelInternal() {
    if (parcels.size() >= MatchRules.MAX_PARCELS_ON_SCREEN) return false;
    if (hubLocations.isEmpty() || targetLocations.isEmpty()) return false;

    Point2D hub = hubLocations.get(random.nextInt(hubLocations.size()));
    int targetId = random.nextInt(targetLocations.size());

    double px = hub.getX() * MatchRules.TILE_SIZE + 20;
    double py = hub.getY() * MatchRules.TILE_SIZE + 20;

    parcels.add(new ParcelLogic(parcelIdCounter++, targetId, px, py));
    return true;
  }

  private void spawnParcel() {
    spawnParcelInternal();
  }

  private void handleInteraction(double dt, VehicleLogic vehicle, PlayerIntent intent) {
    boolean pressed = intent.interact();
    boolean wasPressed = wasInteracting.getOrDefault(vehicle.id(), false);
    wasInteracting.put(vehicle.id(), pressed);

    // Overcooked-style spam: progress decays while you aren't advancing it.
    double progress = interactionGauges.getOrDefault(vehicle.id(), 0.0);
    if (progress > 0.0) {
      progress = Math.max(0.0, progress - (MatchRules.INTERACT_PROGRESS_DECAY_PER_SECOND * dt));
    }

    // Check validity for this frame.
    ParcelLogic pickupTarget = null;
    if (vehicle.canPickup()) {
      pickupTarget = findNearbyParcel(vehicle);
    }

    int deliveryZoneId = findCurrentTargetZoneId(vehicle);
    ParcelLogic carried = findCarriedParcel(vehicle.id());
    boolean deliveryValid =
        carried != null && deliveryZoneId >= 0 && carried.targetHouseId() == deliveryZoneId;

    boolean anyValid = pickupTarget != null || deliveryValid;

    // Advance only on a new press edge while valid.
    boolean justPressed = pressed && !wasPressed;
    if (justPressed && anyValid) {
      progress = Math.min(1.0, progress + (1.0 / MatchRules.INTERACT_TAPS_REQUIRED));
      playSound.accept(AudioKey.INTERACT_TAP);
      pendingSounds.add(AudioKey.INTERACT_TAP);
    }

    interactionGauges.put(vehicle.id(), progress);
    vehicle.setInteractProgress(progress);

    if (progress < 1.0) return;

    // Commit action, then reset.
    if (pickupTarget != null) {
      pickupTarget.pickup(vehicle.id());
      vehicle.setHasParcel(true);
    } else if (deliveryValid) {
      // Deliver the carried parcel.
      for (int i = parcels.size() - 1; i >= 0; i--) {
        ParcelLogic p = parcels.get(i);
        if (p.carrierId() != null && p.carrierId() == vehicle.id()) {
          if (p.targetHouseId() == deliveryZoneId) {
            score += MatchRules.calculateDeliveryScore(p.remainingTime());
            deliveredCount++;
            parcels.remove(i);
            vehicle.setHasParcel(false);
            break;
          }
        }
      }
    }

    interactionGauges.put(vehicle.id(), 0.0);
    vehicle.setInteractProgress(0.0);
  }

  private int findCurrentTargetZoneId(VehicleLogic vehicle) {
    int tx = (int) Math.floor(vehicle.x() / MatchRules.TILE_SIZE);
    int ty = (int) Math.floor(vehicle.y() / MatchRules.TILE_SIZE);

    if (tx < 0 || tx >= tileMap.getWidth() || ty < 0 || ty >= tileMap.getHeight()) {
      return -1;
    }
    if (tileMap.getTile(tx, ty) != TileMap.TileType.TARGET_ZONE) {
      return -1;
    }

    for (int i = 0; i < targetLocations.size(); i++) {
      Point2D loc = targetLocations.get(i);
      if ((int) loc.getX() == tx && (int) loc.getY() == ty) {
        return i;
      }
    }
    return -1;
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
          vehicle.setHasParcel(false);
          vehicle.triggerPickupCooldown();
          playSound.accept(AudioKey.PARCEL_THROW);
          pendingSounds.add(AudioKey.PARCEL_THROW);
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
        vehicle.setHasParcel(true);
        playSound.accept(AudioKey.PARCEL_CATCH);
        pendingSounds.add(AudioKey.PARCEL_CATCH);
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

  private void ensureParcelLandsOnValidTile(ParcelLogic parcel) {
    int tx = (int) Math.floor(parcel.x() / MatchRules.TILE_SIZE);
    int ty = (int) Math.floor(parcel.y() / MatchRules.TILE_SIZE);
    if (tx < 0 || tx >= tileMap.getWidth() || ty < 0 || ty >= tileMap.getHeight()) {
      return;
    }

    if (tileMap.getTile(tx, ty).canParcelLand) {
      return;
    }

    // Try to move the parcel to a neighboring landable tile (4-neighborhood).
    int[][] dirs = new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    for (int[] d : dirs) {
      int nx = tx + d[0];
      int ny = ty + d[1];
      if (nx < 0 || nx >= tileMap.getWidth() || ny < 0 || ny >= tileMap.getHeight()) {
        continue;
      }
      if (!tileMap.getTile(nx, ny).canParcelLand) {
        continue;
      }

      parcel.setX(nx * MatchRules.TILE_SIZE + MatchRules.TILE_SIZE / 2.0);
      parcel.setY(ny * MatchRules.TILE_SIZE + MatchRules.TILE_SIZE / 2.0);
      parcel.land();
      return;
    }

    // No nearby safe spot; still force it to stop to avoid jitter.
    parcel.land();
  }

  public GameState generateSnapshot() {
    List<VehicleState> vStates = vehicles.values().stream().map(VehicleLogic::state).toList();
    List<ParcelState> pStates = parcels.stream().map(ParcelLogic::state).toList();
    return new GameState(
        matchTimer, unhappiness, score, deliveredCount, expiredCount, vStates, pStates, tileMap);
  }

  public List<String> drainSounds() {
    synchronized (pendingSounds) {
      List<String> keys = pendingSounds.stream().map(Enum::name).toList();
      pendingSounds.clear();
      return keys;
    }
  }

  public double getMatchTimer() {
    return matchTimer;
  }

  public void removePlayer(int id) {
    vehicles.remove(id);
    interactionGauges.remove(id);
    wasInteracting.remove(id);
    for (ParcelLogic p : parcels) {
      if (p.carrierId() != null && p.carrierId() == id) {
        p.drop();
      }
    }
  }
}
