package parcelpanic.view;

import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import parcelpanic.media.AssetKeys.ImageKey;
import parcelpanic.media.AssetKeys.ColorKey;
import java.util.EnumMap;

public class AssetManager {
    // Using EnumMap is faster than HashMap for Enums
    private final EnumMap<ImageKey, Image> imageCache = new EnumMap<>(ImageKey.class);

    public Image getImage(ImageKey key) {
        return imageCache.computeIfAbsent(key, k -> {
            String fullPath = "/assets/" + k.getPath();
            return new Image(getClass().getResourceAsStream(fullPath));
        });
    }

    // Helper to convert the Hex string in AssetKeys to a JavaFX Color object
    public Color getColor(ColorKey key) {
        return Color.web(key.getHex());
    }
}