package parcelpanic.world;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.List;

import parcelpanic.world.TileMap.TileType;

public class MapLoader {
    public static TileMap loadFromText(String path) {
        try {
            List<String> lines = Files.readAllLines(Path.of(path));
            if (lines.isEmpty()) throw new IllegalArgumentException("Map is empty");

            int width = 0;
            int height = lines.size();

            for (String line : lines) width = Math.max(width, line.length());

            TileMap map = new TileMap(width, height);

            for (int y = 0; y < height; y++) {
                String line = lines.get(y);
                for (int x = 0; x < width; x++) {
                    if (x >= line.length()) continue;
                    char c = line.charAt(x);
                    switch (c) {
                        case 'W': map.setTile(x, y, TileType.WALL); break;
                        case 'G': map.setTile(x, y, TileType.GRASS); break;
                        case 'H': map.setTile(x, y, TileType.HUB); break;
                        case 'T': map.setTile(x, y, TileType.TARGET_ZONE); break;
                        default: map.setTile(x, y, TileType.ROAD); break;
                    }
                }
            }
            return map;
        } catch (IOException e) { throw new RuntimeException("Failed to load map", e); }
    }
}
