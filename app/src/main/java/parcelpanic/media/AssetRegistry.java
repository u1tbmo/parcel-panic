package parcelpanic.media;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import parcelpanic.media.AssetKeys.ColorKey;
import parcelpanic.media.AssetKeys.FontKey;
import parcelpanic.media.AssetKeys.ImageKey;

/// A registry of assets
public final class AssetRegistry {
  /// A cache of images
  private final Map<String, Image> imageCache = new HashMap<>();
  /// A cache of fonts
  private final Map<String, Font> fontCache = new HashMap<>();
  /// A cache of colors
  private final Map<String, Color> colorCache = new HashMap<>();

  /// Load or retrieve a cached image using an image key
  public Image getImage(ImageKey key, double width, double height) {
    String cacheKey = buildImageCacheKey(key.name(), width, height);
    return imageCache.computeIfAbsent(
        cacheKey,
        k -> {
          try {
            String resourcePath = "/assets/" + key.getPath();
            return new Image(
                getClass().getResourceAsStream(resourcePath), width, height, true, true);
          } catch (Exception e) {
            System.err.println("Failed to load image: " + key.name());
            return createPlaceholderImage(width > 0 ? width : 32, height > 0 ? height : 32);
          }
        });
  }

  /// Load or retrieve a cached image at original size
  public Image getImage(ImageKey key) {
    return getImage(key, 0, 0);
  }

  /// Load or retrieve a cached font using a font key
  public Font getFont(FontKey key) {
    return getFont(key, key.getDefaultSize());
  }

  /// Load or retrieve a cached font using a font key with a size
  public Font getFont(FontKey key, double size) {
    String resourceName = key.getResourceName(size);
    return fontCache.computeIfAbsent(
        resourceName,
        name -> {
          try {
            String resourcePath = "/fonts/" + key.getFamily() + ".ttf";
            InputStream fontStream = getClass().getResourceAsStream(resourcePath);
            if (fontStream == null) {
              return Font.font("System", size);
            }
            Font loaded = Font.loadFont(fontStream, size);
            return loaded != null ? loaded : Font.font("System", size);
          } catch (Exception e) {
            return Font.font("System", size);
          }
        });
  }

  /// Get a predefined UI color using a color key
  public Color getColor(ColorKey key) {
    return colorCache.computeIfAbsent(key.name(), name -> Color.web(key.getHex()));
  }

  /// Clear all caches
  public void clearCache() {
    imageCache.clear();
    fontCache.clear();
    colorCache.clear();
  }

  /// Creates a cache key for an image
  private String buildImageCacheKey(String name, double width, double height) {
    return name + "|" + width + "x" + height;
  }

  /// Creates a placeholder image
  private Image createPlaceholderImage(double width, double height) {
    WritableImage img = new WritableImage((int) width, (int) height);
    PixelWriter writer = img.getPixelWriter();
    for (int x = 0; x < width; x++) {
      for (int y = 0; y < height; y++) {
        writer.setColor(x, y, Color.MAGENTA);
      }
    }
    return img;
  }
}
