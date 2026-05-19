package parcelpanic.screens;

import java.util.EnumMap;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import parcelpanic.input.InputAction;
import parcelpanic.input.InputHintProvider;
import parcelpanic.media.AssetKeys.AudioKey;
import parcelpanic.media.AssetKeys.ColorKey;
import parcelpanic.media.AssetKeys.FontKey;
import parcelpanic.media.UiFactory;
import parcelpanic.runtime.AppContext;
import parcelpanic.screen.Screen;
import parcelpanic.screen.SelectionCursor;
import parcelpanic.util.SmoothedValue;
import parcelpanic.video.VideoManager;

public final class PauseOverlay implements Screen {
  private enum Item {
    RESUME("Resume"),
    VOLUME("Master Volume"),
    MENU("Main Menu");

    private final String text;

    Item(String text) {
      this.text = text;
    }

    public String getText() {
      return text;
    }
  }

  private AppContext ctx;
  private Item selectedItem = Item.RESUME;

  private final Map<Item, Label> menuLabels = new EnumMap<>(Item.class);
  private final Map<Item, SmoothedValue> offsets = new EnumMap<>(Item.class);

  private BorderPane rootPane;
  private Color textColor;
  private Color mutedColor;
  private Color selectedColor;

  @Override
  public void enter(AppContext ctx) {
    this.ctx = ctx;
    this.selectedItem = Item.RESUME;
    this.textColor = ctx.assets().getColor(ColorKey.TEXT_LIGHT);
    this.mutedColor = ctx.assets().getColor(ColorKey.TEXT_MUTED);
    this.selectedColor = ctx.assets().getColor(ColorKey.SUCCESS);

    // Initialize animation offsets
    for (Item item : Item.values()) {
      this.offsets.put(item, new SmoothedValue(0.0, 0.5));
    }

    buildUI();

    rootPane.setFocusTraversable(true);
    javafx.application.Platform.runLater(() -> rootPane.requestFocus());
  }

  @Override
  public void exit() {
    rootPane = null;
  }

  @Override
  public Node getRoot() {
    return rootPane;
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
    Color surfaceBlack = ctx.assets().getColor(ColorKey.SURFACE_BLACK);

    rootPane = UiFactory.createBorderPane(VideoManager.LOGICAL_WIDTH, VideoManager.LOGICAL_HEIGHT);
    rootPane.setBackground(
        UiFactory.createOverlay(
                surfaceBlack, 0.85, VideoManager.LOGICAL_WIDTH, VideoManager.LOGICAL_HEIGHT)
            .getBackground());

    // Title
    Font titleFont = ctx.assets().getFont(FontKey.DISPLAY);
    Label title = UiFactory.createTitle("PAUSED", titleFont, textColor);
    VBox topContainer = new VBox(title);
    topContainer.setAlignment(Pos.TOP_CENTER);
    topContainer.setPadding(new Insets(80, 0, 0, 0));

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

    // Bottom container for hints
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

    Node changeHint =
        UiFactory.createHint(
            InputHintProvider.getIcon(KeyCode.LEFT) + InputHintProvider.getIcon(KeyCode.RIGHT),
            "Adjust",
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

    Node resumeHint =
        UiFactory.createHint(
            InputHintProvider.getIconForAction(InputAction.PAUSE, ctx.settings().controls()),
            "Resume",
            iconFont,
            hintFont,
            hintColor);

    HBox hintsRow = new HBox(40, navigateHint, changeHint, selectHint, resumeHint);
    hintsRow.setAlignment(Pos.CENTER);

    VBox bottomContainer = new VBox(20, hintsRow);
    bottomContainer.setAlignment(Pos.BOTTOM_CENTER);
    bottomContainer.setPadding(new Insets(0, 0, 60, 0));

    rootPane.setTop(topContainer);
    rootPane.setCenter(menuContainer);
    rootPane.setBottom(bottomContainer);

    updateMenuSelection();
  }

  private void updateMenuSelection() {
    for (Item item : Item.values()) {
      Label label = menuLabels.get(item);
      if (label != null) {
        label.setTextFill(item == selectedItem ? selectedColor : mutedColor);
        if (item == Item.VOLUME) {
          label.setText(
              String.format("Master Volume: %d%%", (int) (ctx.audio().getVolume() * 100)));
        }
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
      case UI_LEFT -> {
        if (selectedItem == Item.VOLUME) {
          adjustVolume(-1);
          ctx.audio().playSound(AudioKey.MOVE_SELECTION);
        }
      }
      case UI_RIGHT -> {
        if (selectedItem == Item.VOLUME) {
          adjustVolume(1);
          ctx.audio().playSound(AudioKey.MOVE_SELECTION);
        }
      }
      case CONFIRM -> {
        ctx.audio().playSound(AudioKey.CLICK);
        switch (selectedItem) {
          case RESUME -> ctx.navigator().pop();
          case MENU -> {
            ctx.navigator().pop();
            ctx.navigator().requestSwitch(new MenuScreen());
          }
          default -> {}
        }
      }
      case PAUSE, BACK -> {
        ctx.audio().playSound(AudioKey.CLICK);
        ctx.navigator().pop();
      }
      default -> {}
    }
  }

  private void adjustVolume(int delta) {
    // Prevent conflicting inputs
    if (ctx.input().isPressed(InputAction.UI_LEFT) && ctx.input().isPressed(InputAction.UI_RIGHT)) {
      return;
    }

    // Acceleration logic
    double heldTime =
        (delta < 0)
            ? ctx.input().getHeldTime(InputAction.UI_LEFT)
            : ctx.input().getHeldTime(InputAction.UI_RIGHT);

    double step = 0.01;
    if (heldTime > 2.0) {
      step = 0.10;
    } else if (heldTime > 1.0) {
      step = 0.05;
    } else if (heldTime > 0.5) {
      step = 0.02;
    }

    double newVolume = Math.clamp(ctx.audio().getVolume() + delta * step, 0.0, 1.0);
    ctx.audio().setVolume(newVolume);

    // Auto-save volume in pause screen since there is no "Apply" button
    ctx.settings().audio().setMasterVolume(newVolume);
    ctx.settings().save();
  }
}
