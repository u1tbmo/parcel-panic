package parcelpanic.media;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/// Utility class for creating standardized JavaFX UI components.
public final class UiFactory {
  private UiFactory() {}

  /// Creates a standard label.
  public static Label createLabel(String text, Font font, Color color) {
    Label label = new Label(text);
    label.setFont(font);
    label.setTextFill(color);
    label.setPadding(Insets.EMPTY);
    return label;
  }

  /// Creates a centered title label.
  public static Label createTitle(String text, Font font, Color color) {
    Label label = createLabel(text, font, color);
    label.setAlignment(Pos.CENTER);
    label.setTextAlignment(TextAlignment.CENTER);
    return label;
  }

  /// Creates a hint component with an icon and text.
  public static Node createHint(
      String icon, String text, Font iconFont, Font textFont, Color color) {
    Label iconLabel = createLabel(icon, iconFont, color);
    // Visual adjustment: the icon font is often baseline-heavy, shift it up slightly
    iconLabel.setTranslateY(-3);

    Label hintLabel = createLabel(text, textFont, color);
    hintLabel.setGraphic(iconLabel);
    hintLabel.setContentDisplay(ContentDisplay.LEFT);
    hintLabel.setGraphicTextGap(8);
    hintLabel.setAlignment(Pos.CENTER);
    return hintLabel;
  }

  /// Creates a background region that matches the logical canvas size.
  public static Region createBackground(Color color, double width, double height) {
    Region region = new Region();
    region.setBackground(
        new Background(new BackgroundFill(color, CornerRadii.EMPTY, Insets.EMPTY)));
    region.setPrefSize(width, height);
    region.setMinSize(width, height);
    region.setMaxSize(width, height);
    return region;
  }

  /// Creates a semi-transparent overlay region.
  public static Region createOverlay(Color color, double opacity, double width, double height) {
    Region region = new Region();
    Color transparentColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), opacity);
    region.setBackground(
        new Background(new BackgroundFill(transparentColor, CornerRadii.EMPTY, Insets.EMPTY)));
    region.setPrefSize(width, height);
    region.setMinSize(width, height);
    region.setMaxSize(width, height);
    return region;
  }

  /// Creates a vertical spacer region.
  public static Region createSpacer(double height) {
    Region spacer = new Region();
    spacer.setPrefHeight(height);
    spacer.setMinHeight(height);
    return spacer;
  }

  /// Creates a BorderPane sized to the viewport.
  public static BorderPane createBorderPane(double width, double height) {
    BorderPane pane = new BorderPane();
    pane.setPrefSize(width, height);
    return pane;
  }
}
