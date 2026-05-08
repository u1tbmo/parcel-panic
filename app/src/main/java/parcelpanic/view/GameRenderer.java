package parcelpanic.view;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import parcelpanic.media.AssetKeys.ImageKey;
import parcelpanic.media.AssetRegistry;
import parcelpanic.world.TileMap;
import parcelpanic.world.TileMap.TileType;

public final class GameRenderer {
    private static final int TILE_SIZE = 40; 
    private final AssetRegistry assets;

    public GameRenderer(AssetRegistry assets) {
        this.assets = assets;
    }

    // Renders the complete game state.
    public void render(GraphicsContext gc, parcelpanic.shared.GameState state, double alpha) {
        if (state == null) return;
        renderMap(gc, state.map());
        // TODO: Render vehicles and parcels in future milestones
    }

    // Renders the static world map onto the provided GraphicsContext.
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

    /**
     * Maps the logic Enum to the actual image asset.
     */
    private Image getTextureForType(TileType type) {
        if (type == null) {
            return assets.getImage(ImageKey.TILE_ROAD, TILE_SIZE, TILE_SIZE);
        }
        ImageKey key = switch (type) {
            case WALL -> ImageKey.TILE_WALL;
            case ROAD -> ImageKey.TILE_ROAD;
            case HUB ->     ImageKey.TILE_HUB;
            case TARGET_ZONE -> ImageKey.TILE_TARGET;
            default -> null; 
        };
        return assets.getImage(key, TILE_SIZE, TILE_SIZE);
    }
}