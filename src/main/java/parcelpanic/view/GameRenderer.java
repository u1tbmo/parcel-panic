package parcelpanic.view;

import java.util.HashMap;
import java.util.Map;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import parcelpanic.input.InputAction;
import parcelpanic.input.InputHintProvider;
import parcelpanic.media.AssetKeys.FontKey;
import parcelpanic.media.AssetKeys.ImageKey;
import parcelpanic.media.AssetRegistry;
import parcelpanic.settings.GameSettings.ControlsSettings;
import parcelpanic.shared.GameState;
import parcelpanic.shared.ParcelState;
import parcelpanic.shared.VehicleState;
import parcelpanic.shared.VehicleState.PromptType;
import parcelpanic.world.TileMap;
import parcelpanic.world.TileMap.TileType;

public final class GameRenderer {
  private static final int TILE_SIZE = 40;
  private static final double PROMPT_ICON_SIZE = 38.0;
  private final AssetRegistry assets;
  private final ControlsSettings controls;
  private final Map<Integer, EntityInterpolator> vehicleInterpolators = new HashMap<>();
  private long lastRenderTimeNanos = System.nanoTime();

  public GameRenderer(AssetRegistry assets, ControlsSettings controls) {
    this.assets = assets;
    this.controls = controls;
  }

  public void render(GraphicsContext gc, GameState state, double alpha) {
    if (state == null) return;

    long now = System.nanoTime();
    double dt = Math.min((now - lastRenderTimeNanos) / 1_000_000_000.0, 0.05);
    lastRenderTimeNanos = now;

    gc.setImageSmoothing(false);

    renderMap(gc, state.map());
    renderParcels(gc, state);
    renderVehicles(gc, state, dt);
    renderHouseLabels(gc, state);
  }

  /// Renders the static world map onto the provided GraphicsContext.
  public void renderMap(GraphicsContext gc, TileMap map) {
    if (map == null) return;

    // Fetch full layer images from the asset registry
    Image outerGrass = assets.getImage(ImageKey.MAP_LAYER_GRASS);
    Image buildings = assets.getImage(ImageKey.MAP_LAYER_BUILDINGS);
    Image obstacles = assets.getImage(ImageKey.MAP_LAYER_OBSTACLES);

    var hubTiles = map.getTilesOfType(TileType.HUB);
    for (Point2D tile : hubTiles) {
      Image hubTileImg = getTextureForType(TileType.HUB);

      if (hubTileImg != null) {
        double renderX = tile.getX() * TILE_SIZE;
        double renderY = tile.getY() * TILE_SIZE;

        gc.drawImage(hubTileImg, renderX, renderY);
      }
    }

    // Background Grass
    if (outerGrass != null) {
      gc.drawImage(outerGrass, 0, 0);
    }

    // Buildings & Foreground Structures
    if (buildings != null) {
      gc.drawImage(buildings, 0, 0);
    }

    // Obstacles
    if (obstacles != null) {
      gc.drawImage(obstacles, 0, 0);
    }
  }

  private void renderVehicles(GraphicsContext gc, GameState state, double dt) {
    if (state.vehicles() == null || state.vehicles().isEmpty()) {
      return;
    }

    for (VehicleState vehicle : state.vehicles()) {
      Image carSprite = assets.getImage(getImageKeyForVehicle(vehicle.id(), vehicle.colorIndex()));

      EntityInterpolator interpolator =
          vehicleInterpolators.computeIfAbsent(
              vehicle.id(), id -> new EntityInterpolator(vehicle.x(), vehicle.y(), 50.0));

      interpolator.setTarget(vehicle.x(), vehicle.y());
      interpolator.update(dt);

      double renderX = interpolator.getRenderX();
      double renderY = interpolator.getRenderY();

      gc.save();

      // North=0, East=90, West=270, South=180
      int frameIndex = 0;
      double rot = vehicle.rotation();
      if (rot == 0) frameIndex = 0; // North
      else if (rot == 90) frameIndex = 1; // East
      else if (rot == 270) frameIndex = 2; // West
      else if (rot == 180) frameIndex = 3; // South

      if (carSprite != null) {
        // srcX, srcY, srcW, srcH, dstX, dstY, dstW, dstH
        gc.drawImage(carSprite, frameIndex * 22, 0, 22, 22, renderX - 20, renderY - 20, 40, 40);
      }

      gc.restore();

      // Render momentum indicator OVER the vehicle
      if (vehicle.isAccelerating()) {
        gc.save();
        gc.setFill(Color.WHITE);
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(1.5);
        double dotX = renderX + vehicle.vx() * 0.08;
        double dotY = renderY + vehicle.vy() * 0.08;
        double dotSize = 6;
        gc.fillOval(dotX - dotSize / 2, dotY - dotSize / 2, dotSize, dotSize);
        gc.strokeOval(dotX - dotSize / 2, dotY - dotSize / 2, dotSize, dotSize);
        gc.restore();
      }

      // Render target house ID if vehicle is carrying a parcel
      ParcelState carriedParcel = findCarriedParcel(state, vehicle.id());
      if (carriedParcel != null) {
        gc.save();
        gc.setFont(assets.getFont(FontKey.LABEL, FontKey.LABEL.getDefaultSize()));
        gc.setFill(Color.WHITE);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        String targetNum = String.valueOf(carriedParcel.targetHouseId());
        gc.fillText(targetNum, renderX, renderY - 28);
        gc.restore();
      }

      renderPrompt(gc, state, vehicle, renderX, renderY);
    }
  }

  private void renderPrompt(
      GraphicsContext gc, GameState state, VehicleState vehicle, double renderX, double renderY) {
    PromptType prompt = vehicle.prompt();
    if (prompt == null || prompt == PromptType.NONE) {
      return;
    }

    double centerX;
    double centerY;

    // For DELIVER_WRONG, show at vehicle's current location (the wrong house).
    // For other prompts (PICKUP and DELIVER_OK), show at the target.
    if (prompt == PromptType.PICKUP) {
      ParcelState targetParcel = findNearestInteractableParcel(state, vehicle);
      if (targetParcel == null) {
        return;
      }
      centerX = targetParcel.x();
      centerY = targetParcel.y() - targetParcel.z() - 28;
    } else {
      if (prompt == PromptType.DELIVER_WRONG) {
        // Wrong house: show at vehicle's current location
        centerX = renderX;
        centerY = renderY - 40;
      } else {
        // Correct house: show at target zone
        ParcelState carriedParcel = findCarriedParcel(state, vehicle.id());
        if (carriedParcel == null || state.map() == null) {
          return;
        }

        Point2D targetCenter = findTargetZoneCenter(state.map(), carriedParcel.targetHouseId());
        if (targetCenter == null) {
          return;
        }

        centerX = targetCenter.getX();
        centerY = targetCenter.getY() - 12;
      }
    }

    gc.save();

    if (prompt == PromptType.DELIVER_WRONG) {
      Image cross = assets.getImage(ImageKey.EMOTE_CROSS, PROMPT_ICON_SIZE, PROMPT_ICON_SIZE);
      if (cross != null) {
        gc.drawImage(
            cross,
            centerX - PROMPT_ICON_SIZE / 2.0,
            centerY - PROMPT_ICON_SIZE / 2.0,
            PROMPT_ICON_SIZE,
            PROMPT_ICON_SIZE);
      }
    } else {
      gc.setFont(assets.getFont(FontKey.HINT, FontKey.HINT.getDefaultSize()));
      gc.setFill(Color.WHITE);
      gc.setTextAlign(TextAlignment.CENTER);
      gc.setTextBaseline(VPos.CENTER);
      gc.fillText(
          InputHintProvider.getIconForAction(InputAction.INTERACT, controls), centerX, centerY);
    }

    gc.restore();
  }

  private ParcelState findNearestInteractableParcel(GameState state, VehicleState vehicle) {
    if (state.parcels() == null || state.parcels().isEmpty()) {
      return null;
    }

    ParcelState nearest = null;
    double bestDistance = Double.MAX_VALUE;
    for (ParcelState parcel : state.parcels()) {
      if (parcel.carrierId() != null) {
        continue;
      }

      double distance = Math.hypot(parcel.x() - vehicle.x(), parcel.y() - vehicle.y());
      if (distance < bestDistance) {
        bestDistance = distance;
        nearest = parcel;
      }
    }

    return nearest;
  }

  private ParcelState findCarriedParcel(GameState state, int vehicleId) {
    if (state.parcels() == null || state.parcels().isEmpty()) {
      return null;
    }

    for (ParcelState parcel : state.parcels()) {
      if (parcel.carrierId() != null && parcel.carrierId() == vehicleId) {
        return parcel;
      }
    }

    return null;
  }

  private Point2D findTargetZoneCenter(TileMap map, int targetHouseId) {
    var targets = map.getTilesOfType(TileType.TARGET_ZONE);
    if (targetHouseId < 0 || targetHouseId >= targets.size()) {
      return null;
    }

    Point2D tile = targets.get(targetHouseId);
    return new javafx.geometry.Point2D(
        tile.getX() * TILE_SIZE + TILE_SIZE / 2.0, tile.getY() * TILE_SIZE + TILE_SIZE / 2.0);
  }

  private void renderParcels(GraphicsContext gc, GameState state) {
    if (state.parcels() == null || state.parcels().isEmpty()) {
      return;
    }

    Image parcelSprite = assets.getImage(ImageKey.ENTITY_PARCEL);

    for (ParcelState parcel : state.parcels()) {
      // Don't render if carried (or render with the vehicle)
      if (parcel.carrierId() != null) continue;

      // Draw a shadow to ground the visual arc
      if (parcel.z() > 0) {
        gc.setFill(Color.rgb(0, 0, 0, 0.3));
        double shadowSize =
            Math.max(10, 20 - parcel.z() * 0.2); // Shadow gets smaller as it goes up
        gc.fillOval(
            parcel.x() - shadowSize / 2, parcel.y() - shadowSize / 4, shadowSize, shadowSize / 2);
      }

      if (parcelSprite != null) {
        // Draw the parcel sprite centered and elevated by Z
        gc.drawImage(parcelSprite, parcel.x() - 16, parcel.y() - 16 - parcel.z(), 32, 32);
      } else {
        gc.setFill(parcel.isDamaged() ? Color.RED : Color.BROWN);
        gc.fillRect(parcel.x() - 10, parcel.y() - 10 - parcel.z(), 20, 20);
      }
    }
  }

  /** Maps the logic Enum to the actual image asset. */
  private Image getTextureForType(TileType type) {
    if (type == null) {
      return assets.getImage(ImageKey.TILE_ROAD, TILE_SIZE, TILE_SIZE);
    }
    ImageKey key =
        switch (type) {
          case WALL -> ImageKey.TILE_WALL;
          case ROAD -> ImageKey.TILE_ROAD;
          case HUB -> ImageKey.TILE_HUB;
          case TARGET_ZONE -> ImageKey.TILE_TARGET;
          default -> null;
        };
    if (key == null) return null;
    return assets.getImage(key, TILE_SIZE, TILE_SIZE);
  }

  /** Renders house labels (numbers) at the bottom of each TARGET_ZONE tile. */
  private void renderHouseLabels(GraphicsContext gc, GameState state) {

    if (state.map() == null) return;

    Image targetCallImg = assets.getImage(ImageKey.MAP_LAYER_TARGET);

    if (targetCallImg != null) {
      gc.save();
      gc.drawImage(targetCallImg, 0, 0);
      gc.restore();
    }
  }

  public static ImageKey getImageKeyForVehicle(int id, int colorIndex) {
    int color = colorIndex / 10;
    long time = System.currentTimeMillis() + id * 120L;
    int style = ((time / 500) % 2 == 0) ? 1 : 2;
    return switch (color) {
      case 0 -> (style == 1) ? ImageKey.VEHICLE_CAR_RED_1 : ImageKey.VEHICLE_CAR_RED_2;
      case 1 -> (style == 1) ? ImageKey.VEHICLE_CAR_BLUE_1 : ImageKey.VEHICLE_CAR_BLUE_2;
      case 2 -> (style == 1) ? ImageKey.VEHICLE_CAR_GREEN_1 : ImageKey.VEHICLE_CAR_GREEN_2;
      case 3 -> (style == 1) ? ImageKey.VEHICLE_CAR_YELLOW_1 : ImageKey.VEHICLE_CAR_YELLOW_2;
      case 4 -> (style == 1) ? ImageKey.VEHICLE_CAR_ORANGE_1 : ImageKey.VEHICLE_CAR_ORANGE_2;
      case 5 -> (style == 1) ? ImageKey.VEHICLE_CAR_PINK_1 : ImageKey.VEHICLE_CAR_PINK_2;
      case 6 -> (style == 1) ? ImageKey.VEHICLE_CAR_MAGENTA_1 : ImageKey.VEHICLE_CAR_MAGENTA_2;
      default -> ImageKey.VEHICLE_CAR;
    };
  }
}
