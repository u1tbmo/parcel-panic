package parcelpanic.view;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import parcelpanic.media.AssetKeys.ImageKey;
import parcelpanic.media.AssetRegistry;
import parcelpanic.shared.GameState;
import parcelpanic.shared.ParcelState;
import parcelpanic.shared.VehicleState;
import parcelpanic.world.TileMap;
import parcelpanic.world.TileMap.TileType;

public final class GameRenderer {
  private static final int TILE_SIZE = 40;
  private final AssetRegistry assets;

  public GameRenderer(AssetRegistry assets) {
    this.assets = assets;
  }

  public void render(GraphicsContext gc, GameState state, double alpha) {
    if (state == null) return;

    gc.setImageSmoothing(false);

    renderMap(gc, state.map());
    renderParcels(gc, state);
    renderVehicles(gc, state);
  }

  /** Renders the static world map onto the provided GraphicsContext. */
  public void renderMap(GraphicsContext gc, TileMap map) {
    if (map == null) return;

    for (int y = 0; y < map.getHeight(); y++) {
      for (int x = 0; x < map.getWidth(); x++) {
        TileType type = map.getTile(x, y);
        Image img = getTextureForType(type);

        if (img != null) {
          // Draw the image at grid coordinates multiplied by tile size
          gc.drawImage(img, x * TILE_SIZE, y * TILE_SIZE, TILE_SIZE, TILE_SIZE);
        }
      }
    }
  }

  private void renderVehicles(GraphicsContext gc, GameState state) {
    if (state.vehicles() == null || state.vehicles().isEmpty()) {
      return;
    }

    Image carSprite = assets.getImage(ImageKey.VEHICLE_CAR);

    for (VehicleState vehicle : state.vehicles()) {
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
        gc.drawImage(
            carSprite, frameIndex * 22, 0, 22, 22, vehicle.x() - 20, vehicle.y() - 20, 40, 40);
      }

      gc.restore();

      // Render momentum indicator OVER the vehicle
      if (vehicle.isAccelerating()) {
        gc.save();
        gc.setFill(Color.WHITE);
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(1.5);
        double dotX = vehicle.x() + vehicle.vx() * 0.08;
        double dotY = vehicle.y() + vehicle.vy() * 0.08;
        double dotSize = 6;
        gc.fillOval(dotX - dotSize / 2, dotY - dotSize / 2, dotSize, dotSize);
        gc.strokeOval(dotX - dotSize / 2, dotY - dotSize / 2, dotSize, dotSize);
        gc.restore();
      }
    }
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
}
