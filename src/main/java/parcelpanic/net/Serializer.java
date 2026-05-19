package parcelpanic.net;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import parcelpanic.shared.GameState;
import parcelpanic.shared.ParcelState;
import parcelpanic.shared.PlayerIntent;
import parcelpanic.shared.VehicleState;
import parcelpanic.shared.VehicleState.PromptType;

/// Serializes and deserializes game objects to/from JSON for network transmission.
public class Serializer {
  private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

  // ============ GAMESTATE SERIALIZATION ============

  public static String serializeGameState(GameState state) {
    if (state == null) {
      return null;
    }

    JsonObject root = new JsonObject();
    root.addProperty("matchTimer", state.matchTimer());
    root.addProperty("unhappiness", state.unhappiness());
    root.addProperty("score", state.score());

    // Serialize vehicles
    JsonArray vehiclesArray = new JsonArray();
    if (state.vehicles() != null) {
      for (VehicleState v : state.vehicles()) {
        vehiclesArray.add(serializeVehicleState(v));
      }
    }
    root.add("vehicles", vehiclesArray);

    // Serialize parcels
    JsonArray parcelsArray = new JsonArray();
    if (state.parcels() != null) {
      for (ParcelState p : state.parcels()) {
        parcelsArray.add(serializeParcelState(p));
      }
    }
    root.add("parcels", parcelsArray);

    // Note: TileMap is static and doesn't need to be serialized; server/client both load it
    // from the same map file. If needed, add mapId or mapData here.

    return gson.toJson(root);
  }

  public static GameState deserializeGameState(String json) {
    if (json == null || json.isEmpty()) {
      return null;
    }

    try {
      JsonObject root = gson.fromJson(json, JsonObject.class);

      double matchTimer = root.get("matchTimer").getAsDouble();
      double unhappiness = root.get("unhappiness").getAsDouble();
      double score = root.get("score").getAsDouble();

      // Deserialize vehicles
      List<VehicleState> vehicles = new ArrayList<>();
      JsonArray vehiclesArray = root.getAsJsonArray("vehicles");
      if (vehiclesArray != null) {
        for (var element : vehiclesArray) {
          vehicles.add(deserializeVehicleState(element.getAsJsonObject()));
        }
      }

      // Deserialize parcels
      List<ParcelState> parcels = new ArrayList<>();
      JsonArray parcelsArray = root.getAsJsonArray("parcels");
      if (parcelsArray != null) {
        for (var element : parcelsArray) {
          parcels.add(deserializeParcelState(element.getAsJsonObject()));
        }
      }

      // TileMap will be set by the client from the local map file
      return new GameState(matchTimer, unhappiness, score, vehicles, parcels, null);
    } catch (Exception e) {
      System.err.println("Error deserializing GameState: " + e.getMessage());
      return null;
    }
  }

  private static JsonObject serializeVehicleState(VehicleState v) {
    JsonObject obj = new JsonObject();
    obj.addProperty("id", v.id());
    obj.addProperty("x", v.x());
    obj.addProperty("y", v.y());
    obj.addProperty("vx", v.vx());
    obj.addProperty("vy", v.vy());
    obj.addProperty("rotation", v.rotation());
    obj.addProperty("isDashing", v.isDashing());
    obj.addProperty("isAccelerating", v.isAccelerating());
    obj.addProperty("prompt", v.prompt().name());
    obj.addProperty("colorIndex", v.colorIndex());
    return obj;
  }

  private static VehicleState deserializeVehicleState(JsonObject obj) {
    int id = obj.get("id").getAsInt();
    double x = obj.get("x").getAsDouble();
    double y = obj.get("y").getAsDouble();
    double vx = obj.get("vx").getAsDouble();
    double vy = obj.get("vy").getAsDouble();
    double rotation = obj.get("rotation").getAsDouble();
    boolean isDashing = obj.get("isDashing").getAsBoolean();
    boolean isAccelerating = obj.get("isAccelerating").getAsBoolean();
    PromptType prompt = PromptType.valueOf(obj.get("prompt").getAsString());
    int colorIndex =
        obj.has("colorIndex") ? obj.get("colorIndex").getAsInt() : 1; // default to Red style 1

    return new VehicleState(
        id, x, y, vx, vy, rotation, isDashing, isAccelerating, prompt, colorIndex);
  }

  private static JsonObject serializeParcelState(ParcelState p) {
    JsonObject obj = new JsonObject();
    obj.addProperty("id", p.id());
    obj.addProperty("x", p.x());
    obj.addProperty("y", p.y());
    obj.addProperty("z", p.z());
    obj.add("carrierId", p.carrierId() == null ? null : gson.toJsonTree(p.carrierId()));
    obj.addProperty("remainingTime", p.remainingTime());
    obj.addProperty("isDamaged", p.isDamaged());
    obj.addProperty("targetHouseId", p.targetHouseId());
    return obj;
  }

  private static ParcelState deserializeParcelState(JsonObject obj) {
    int id = obj.get("id").getAsInt();
    double x = obj.get("x").getAsDouble();
    double y = obj.get("y").getAsDouble();
    double z = obj.get("z").getAsDouble();
    // Handle nullable carrierId: check if element exists first
    Integer carrierId = null;
    if (obj.has("carrierId") && !obj.get("carrierId").isJsonNull()) {
      carrierId = obj.get("carrierId").getAsInt();
    }
    double remainingTime = obj.get("remainingTime").getAsDouble();
    boolean isDamaged = obj.get("isDamaged").getAsBoolean();
    int targetHouseId = obj.get("targetHouseId").getAsInt();

    return new ParcelState(id, x, y, z, carrierId, remainingTime, isDamaged, targetHouseId);
  }

  // ============ PLAYERINTENT SERIALIZATION ============

  public static String serializePlayerIntent(PlayerIntent intent) {
    if (intent == null) {
      return null;
    }

    JsonObject obj = new JsonObject();
    obj.addProperty("playerId", intent.playerId());
    obj.addProperty("up", intent.up());
    obj.addProperty("down", intent.down());
    obj.addProperty("left", intent.left());
    obj.addProperty("right", intent.right());
    obj.addProperty("dash", intent.dash());
    obj.addProperty("interact", intent.interact());
    obj.addProperty("throwParcel", intent.throwParcel());

    return gson.toJson(obj);
  }

  public static PlayerIntent deserializePlayerIntent(String json) {
    if (json == null || json.isEmpty()) {
      return null;
    }

    try {
      JsonObject obj = gson.fromJson(json, JsonObject.class);

      int playerId = obj.get("playerId").getAsInt();
      boolean up = obj.get("up").getAsBoolean();
      boolean down = obj.get("down").getAsBoolean();
      boolean left = obj.get("left").getAsBoolean();
      boolean right = obj.get("right").getAsBoolean();
      boolean dash = obj.get("dash").getAsBoolean();
      boolean interact = obj.get("interact").getAsBoolean();
      boolean throwParcel = obj.get("throwParcel").getAsBoolean();

      return new PlayerIntent(playerId, up, down, left, right, dash, interact, throwParcel);
    } catch (Exception e) {
      System.err.println("Error deserializing PlayerIntent: " + e.getMessage());
      return null;
    }
  }
}
