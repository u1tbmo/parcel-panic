package parcelpanic.screens;

import java.util.EnumMap;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import parcelpanic.input.InputAction;
import parcelpanic.input.InputHintProvider;
import parcelpanic.media.AssetKeys.AudioKey;
import parcelpanic.media.AssetKeys.ColorKey;
import parcelpanic.media.AssetKeys.FontKey;
import parcelpanic.media.AssetKeys.ImageKey;
import parcelpanic.media.UiFactory;
import parcelpanic.net.LobbyScreen;
import parcelpanic.screen.ContentScreen;
import parcelpanic.screen.SelectionCursor;
import parcelpanic.util.SmoothedValue;
import parcelpanic.video.VideoManager;

public final class MenuScreen extends ContentScreen {
  private enum Item {
    SINGLEPLAYER("Singleplayer"),
    MULTIPLAYER("Multiplayer"),
    OPTIONS("Options"),
    EXIT("Exit");

    private final String text;

    Item(String text) {
      this.text = text;
    }

    public String getText() {
      return text;
    }
  }

  private Item selectedItem = Item.SINGLEPLAYER;

  private final Map<Item, Label> menuLabels = new EnumMap<>(Item.class);
  private final Map<Item, SmoothedValue> offsets = new EnumMap<>(Item.class);

  private StackPane rootContainer; 
  private BorderPane uiOverlayPane; 
  private Color textColor;
  private Color mutedColor;
  private Color selectedColor;

  @Override
  protected VideoManager.ViewportMode viewportMode() {
    return VideoManager.ViewportMode.FULL_WINDOW;
  }

  @Override
  protected void onBeforeBuild() {
    this.selectedItem = Item.SINGLEPLAYER;
    this.textColor = ctx.assets().getColor(ColorKey.TEXT_LIGHT);
    this.mutedColor = ctx.assets().getColor(ColorKey.TEXT_MUTED);
    this.selectedColor = ctx.assets().getColor(ColorKey.SUCCESS);

    // Initialize animation offsets
    for (Item item : Item.values()) {
      this.offsets.put(item, new SmoothedValue(0.0, 0.5));
    }
  }

  @Override
  protected Node createContent() {
    buildUI();
    return rootContainer; 
  }

  @Override
  public void fixedUpdate(double dtSeconds) {
    for (Item item : Item.values()) {
      double target = (item == selectedItem) ? 12.0 : 0.0;
      offsets.get(item).update(target);
    }
  }

  @Override
  public void render(double alpha) {
    updateMenuSelection();

    // Apply smooth offsets to labels
    for (Item item : Item.values()) {
      Label label = menuLabels.get(item);
      if (label != null) {
        label.setTranslateX(offsets.get(item).get(alpha));
      }
    }
  }

  private void buildUI() {

    rootContainer = new StackPane();
    rootContainer.setPrefSize(VideoManager.LOGICAL_WIDTH, VideoManager.LOGICAL_HEIGHT);

    Image gifBackground = ctx.assets().getImage(ImageKey.MENU_MAIN);
    if (gifBackground != null) {
      ImageView bgImageView = new ImageView(gifBackground);
      bgImageView.setFitWidth(VideoManager.LOGICAL_WIDTH);
      bgImageView.setFitHeight(VideoManager.LOGICAL_HEIGHT);
      bgImageView.setPreserveRatio(false);
      bgImageView.setSmooth(false); 
      
      rootContainer.getChildren().add(bgImageView);
    }


    uiOverlayPane = UiFactory.createBorderPane(VideoManager.LOGICAL_WIDTH, VideoManager.LOGICAL_HEIGHT);
    uiOverlayPane.setBackground(null); 

    // Title
    // Font titleFont = ctx.assets().getFont(FontKey.DISPLAY);
    // Label title = UiFactory.createTitle("Parcel Panic", titleFont, textColor);
    // VBox topContainer = new VBox(title);
    // topContainer.setAlignment(Pos.TOP_CENTER);
    // topContainer.setPadding(new Insets(80, 0, 0, 0));

    // Menu items
    Font menuFont = ctx.assets().getFont(FontKey.TITLE);
    VBox menuContainer = new VBox(20);
    menuContainer.setAlignment(Pos.CENTER_LEFT);
    menuContainer.setPadding(new Insets(0, 0, 0, 100));

    for (Item item : Item.values()) {
      Label label = UiFactory.createLabel(item.getText(), menuFont, textColor);
      menuLabels.put(item, label);
      menuContainer.getChildren().add(label);
    }

    // Bottom container for hints and version
    Font hintFont = ctx.assets().getFont(FontKey.LABEL);
    Font iconFont = ctx.assets().getFont(FontKey.HINT);
    Color hintColor = ctx.assets().getColor(ColorKey.TEXT_HINT);

    Node navigateHint =
        UiFactory.createHint(
            InputHintProvider.getIcon(KeyCode.UP) + InputHintProvider.getIcon(KeyCode.DOWN),
            "Navigate",
            iconFont,
            hintFont,
            hintColor);

    Node selectHint =
        UiFactory.createHint(
            InputHintProvider.getIconForAction(InputAction.CONFIRM, ctx.settings().controls()),
            "Select",
            iconFont,
            hintFont,
            hintColor);

    HBox hintsRow = new HBox(40, navigateHint, selectHint);
    hintsRow.setAlignment(Pos.CENTER);

    Label version = UiFactory.createLabel("Version 1.0", hintFont, hintColor);

    VBox bottomContainer = new VBox(20, hintsRow, version);
    bottomContainer.setAlignment(Pos.BOTTOM_CENTER);
    bottomContainer.setPadding(new Insets(0, 0, 60, 0));


    // uiOverlayPane.setTop(topContainer);
    uiOverlayPane.setCenter(menuContainer);
    uiOverlayPane.setBottom(bottomContainer);


    rootContainer.getChildren().add(uiOverlayPane);

    updateMenuSelection();
  }

  private void updateMenuSelection() {
    for (Item item : Item.values()) {
      Label label = menuLabels.get(item);
      if (label != null) {
        label.setTextFill(item == selectedItem ? selectedColor : mutedColor);
      }
    }
  }

  @Override
  public boolean supportsInputRepeat() {
    return true;
  }

  @Override
  public void onKeyPressed(InputAction action) {
    switch (action) {
      case UI_UP -> {
        selectedItem = SelectionCursor.move(Item.values(), selectedItem, -1);
        ctx.audio().playSound(AudioKey.MOVE_SELECTION);
      }
      case UI_DOWN -> {
        selectedItem = SelectionCursor.move(Item.values(), selectedItem, 1);
        ctx.audio().playSound(AudioKey.MOVE_SELECTION);
      }
      case CONFIRM -> {
        ctx.audio().playSound(AudioKey.CLICK);
        switch (selectedItem) {
          case SINGLEPLAYER -> ctx.navigator().requestSwitch(new MatchScreen());
          case MULTIPLAYER -> ctx.navigator().requestSwitch(new LobbyScreen());
          case OPTIONS -> ctx.navigator().requestSwitch(new OptionsScreen());
          case EXIT -> ctx.stage().close();
        }
      }
      default -> {}
    }
  }
}