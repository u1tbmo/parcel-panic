package parcelpanic.view;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
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

    renderMap(gc, state.map());
    renderParcels(gc, state);
    renderVehicles(gc, state);
    renderHUD(gc, state);
  }

  public void renderMap(GraphicsContext gc, TileMap map) {
    if (map == null) return;

    for (int y = 0; y < map.getHeight(); y++) {
      for (int x = 0; x < map.getWidth(); x++) {
        TileType type = map.getTile(x, y);
        Image img = getTextureForType(type);

        if (img != null) {
          gc.drawImage(img, x * TILE_SIZE, y * TILE_SIZE, TILE_SIZE, TILE_SIZE);
        }
      }
    }
  }

  private void renderVehicles(GraphicsContext gc, GameState state) {
    if (state.vehicles() == null || state.vehicles().isEmpty()) {
      return;
    }

    for (VehicleState vehicle : state.vehicles()) {
      gc.save();

      gc.translate(vehicle.x(), vehicle.y());
      gc.rotate(vehicle.rotation());

      gc.setFill(Color.BLUE);
      gc.fillRect(-20, -12, 40, 24);

      gc.setFill(Color.WHITE);
      gc.fillRect(5, -8, 10, 16);

      gc.restore();
    }
  }

  private void renderParcels(GraphicsContext gc, GameState state) {
    if (state.parcels() == null || state.parcels().isEmpty()) {
      return;
    }

    for (ParcelState parcel : state.parcels()) {
      gc.setFill(parcel.isDamaged() ? Color.RED : Color.BROWN);
      gc.fillRect(parcel.x() - 10, parcel.y() - 10, 20, 20);
    }
  }

  private void renderHUD(GraphicsContext gc, GameState state) {
    gc.setFill(Color.WHITE);
    gc.setFont(Font.font(16));

    gc.fillText("Time: " + Math.max(0, (int) state.matchTimer()), 30, 30);
    gc.fillText("Unhappiness: " + (int) (state.unhappiness() * 100) + "%", 30, 55);
  }

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
