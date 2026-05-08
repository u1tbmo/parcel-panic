package parcelpanic.world;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import parcelpanic.world.TileMap.TileType;

public class MapLoader {
    public static TileMap loadFromText(String resourcePath) {
        // Use getResourceAsStream to look inside the src/main/resources folder
        try (InputStream is = MapLoader.class.getResourceAsStream(resourcePath)) {
            
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            List<String> lines = reader.lines().collect(Collectors.toList());
            
            if (lines.isEmpty()) throw new IllegalArgumentException("Map is empty");

            int height = lines.size();
            int width = 0;
            for (String line : lines) width = Math.max(width, line.length());

            TileMap map = new TileMap(width, height);

            for (int y = 0; y < height; y++) {
                String line = lines.get(y);
                for (int x = 0; x < width; x++) {
                    if (x >= line.length()) {
                        map.setTile(x, y, TileType.ROAD); // Fill gaps with road
                        continue;
                    }
                    char c = line.charAt(x);
                    TileType type = switch (c) {
                        case 'W' -> TileType.WALL;
                        case 'G' -> TileType.GRASS;
                        case 'H' -> TileType.HUB;
                        case 'T' -> TileType.TARGET_ZONE;
                        default -> TileType.ROAD;
                    };
                    map.setTile(x, y, type);
                }
            }
            return map;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load map from resources: " + resourcePath, e);
        }
    }
}