package parcelpanic.world;

public class TileMap {
    private final int width;
    private final int height;
    private final TileType[][] grid;

    public enum TileType {
        ROAD(false), 
        GRASS(false), 
        WALL(true), 
        HUB(false), 
        TARGET_ZONE(false);

        public final boolean isSolid;
        TileType(boolean isSolid) { this.isSolid = isSolid; }
    }

    public TileMap(int width, int height) {
        this.width = width;
        this.height = height;
        this.grid = new TileType[width][height];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                grid[x][y] = TileType.ROAD; 
            }
        }
    }

    public void setTile(int x, int y, TileType type) { grid[x][y] = type; }
    public TileType getTile(int x, int y) { return grid[x][y]; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}