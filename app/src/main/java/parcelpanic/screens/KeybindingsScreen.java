package parcelpanic.screens;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
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
import parcelpanic.screen.ContentScreen;
import parcelpanic.video.VideoManager;

public final class KeybindingsScreen extends ContentScreen {
  private enum State {
    NAVIGATION,
    LISTENING
  }

  private enum SpecialOption {
    RESET_DEFAULTS,
    BACK
  }

  private State state = State.NAVIGATION;
  private int selectedIndex = 0;
  private boolean isSpecialOptionSelected = false;
  private SpecialOption selectedSpecial = SpecialOption.BACK;

  private final InputAction[] actions = InputAction.values();
  private final Map<InputAction, Label> actionLabels = new EnumMap<>(InputAction.class);
  private final Map<InputAction, HBox> bindingContainers = new EnumMap<>(InputAction.class);
  private final Map<SpecialOption, Label> specialLabels = new EnumMap<>(SpecialOption.class);

  private BorderPane rootPane;
  private Color textColor;
  private Color mutedColor;
  private Color selectedColor;
  private Color listeningColor;

  @Override
  protected VideoManager.ViewportMode viewportMode() {
    return VideoManager.ViewportMode.FULL_WINDOW;
  }

  @Override
  protected void onBeforeBuild() {
    this.textColor = ctx.assets().getColor(ColorKey.TEXT_LIGHT);
    this.mutedColor = ctx.assets().getColor(ColorKey.TEXT_MUTED);
    this.selectedColor = ctx.assets().getColor(ColorKey.SUCCESS);
    this.listeningColor = ctx.assets().getColor(ColorKey.WARNING);
  }

  @Override
  protected Node createContent() {
    buildUI();
    return rootPane;
  }

  @Override
  public void fixedUpdate(double dtSeconds) {
    // No-op: Animations removed as requested
  }

  @Override
  public void render(double alpha) {
    updateDisplay();
  }

  private void buildUI() {
    rootPane = UiFactory.createBorderPane(VideoManager.LOGICAL_WIDTH, VideoManager.LOGICAL_HEIGHT);
    rootPane.setBackground(
        UiFactory.createBackground(
                ctx.assets().getColor(ColorKey.SURFACE_BLACK),
                VideoManager.LOGICAL_WIDTH,
                VideoManager.LOGICAL_HEIGHT)
            .getBackground());

    VBox top = createTopContainer();
    Node center = createCenterContainer();
    VBox bottom = createBottomContainer();

    VBox centerWrapper = new VBox(20);
    centerWrapper.setAlignment(Pos.TOP_CENTER);
    
    // Add ScrollPane for actions
    centerWrapper.getChildren().add(center);
    
    // Add Special Options below ScrollPane
    VBox specialList = new VBox(15);
    specialList.setAlignment(Pos.CENTER);
    Font labelFont = ctx.assets().getFont(FontKey.LABEL);
    for (SpecialOption opt : SpecialOption.values()) {
      String text = opt == SpecialOption.RESET_DEFAULTS ? "Reset to Defaults" : "Back";
      Label label = UiFactory.createLabel(text, labelFont, mutedColor);
      specialLabels.put(opt, label);
      specialList.getChildren().add(label);
    }
    centerWrapper.getChildren().add(specialList);

    rootPane.setTop(top);
    rootPane.setCenter(centerWrapper);
    rootPane.setBottom(bottom);
  }

  private VBox createTopContainer() {
    Font titleFont = ctx.assets().getFont(FontKey.HEADLINE);
    Label title = UiFactory.createTitle("Keybindings", titleFont, textColor);
    VBox container = new VBox(title);
    container.setAlignment(Pos.TOP_CENTER);
    container.setPadding(new Insets(60, 0, 20, 0));
    return container;
  }

  private Node createCenterContainer() {
    VBox content = new VBox(30);
    content.setAlignment(Pos.TOP_CENTER);
    content.setPadding(new Insets(0, 0, 20, 0));
    content.setBackground(null);

    Font labelFont = ctx.assets().getFont(FontKey.LABEL);
    Font categoryFont = ctx.assets().getFont(FontKey.TITLE);

    // UI / Interface Category
    content.getChildren().add(UiFactory.createLabel("UI / Interface", categoryFont, textColor));
    VBox uiList = new VBox(10);
    uiList.setAlignment(Pos.CENTER);
    for (InputAction action : actions) {
      if (action.name().startsWith("UI_") || action == InputAction.CONFIRM || action == InputAction.BACK || action == InputAction.PAUSE) {
        uiList.getChildren().add(createActionRow(action, labelFont));
      }
    }
    content.getChildren().add(uiList);

    // Gameplay Category
    content.getChildren().add(UiFactory.createLabel("Gameplay", categoryFont, textColor));
    VBox gameplayList = new VBox(10);
    gameplayList.setAlignment(Pos.CENTER);
    for (InputAction action : actions) {
      if (action.name().startsWith("MOVE_") || action == InputAction.INTERACT || action == InputAction.DASH || action == InputAction.THROW) {
        gameplayList.getChildren().add(createActionRow(action, labelFont));
      }
    }
    content.getChildren().add(gameplayList);

    ScrollPane scrollPane = new ScrollPane(content);
    scrollPane.setFitToWidth(true);
    scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scrollPane.setPrefHeight(400); 
    scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-viewport-background-color: transparent;");
    scrollPane.setPannable(false);
    scrollPane.setFocusTraversable(false);

    return scrollPane;
  }

  private HBox createActionRow(InputAction action, Font labelFont) {
    String displayName = action.name().replace("UI_", "").replace("MOVE_", "").replace("_", " ");
    Label actionLabel = UiFactory.createLabel(displayName, labelFont, textColor);
    HBox bindingBox = new HBox(15);
    bindingBox.setAlignment(Pos.CENTER_LEFT);

    actionLabels.put(action, actionLabel);
    bindingContainers.put(action, bindingBox);

    HBox row = new HBox(40, actionLabel, bindingBox);
    row.setAlignment(Pos.CENTER);
    actionLabel.setPrefWidth(180);
    actionLabel.setAlignment(Pos.CENTER_RIGHT);
    bindingBox.setPrefWidth(180);
    bindingBox.setAlignment(Pos.CENTER_LEFT);
    
    return row;
  }

  private VBox createBottomContainer() {
    VBox container = new VBox(20);
    container.setAlignment(Pos.BOTTOM_CENTER);
    container.setPadding(new Insets(20, 0, 40, 0));
    return container;
  }

  private void updateDisplay() {
    Font iconFont = ctx.assets().getFont(FontKey.HINT);

    // Update auto-scroll
    if (!isSpecialOptionSelected && rootPane.getCenter() instanceof VBox wrapper) {
      if (wrapper.getChildren().get(0) instanceof ScrollPane scroll) {
        double vValue = (double) selectedIndex / (actions.length - 1);
        scroll.setVvalue(vValue);
      }
    }

    // Update action bindings
    for (InputAction action : actions) {
      Label aLabel = actionLabels.get(action);
      HBox bBox = bindingContainers.get(action);

      if (aLabel == null || bBox == null) continue;

      bBox.getChildren().clear();

      if (state == State.LISTENING
          && !isSpecialOptionSelected
          && actions[selectedIndex] == action) {
        aLabel.setTextFill(listeningColor);
        Label listening =
            UiFactory.createLabel(
                "Listening...", ctx.assets().getFont(FontKey.LABEL), listeningColor);
        bBox.getChildren().add(listening);
      } else {
        List<KeyCode> keys = ctx.settings().controls().getKeysForAction(action);
        Color keyColor =
            (!isSpecialOptionSelected && actions[selectedIndex] == action)
                ? selectedColor
                : mutedColor;

        for (KeyCode k : keys) {
          Label iconLabel = UiFactory.createLabel(InputHintProvider.getIcon(k), iconFont, keyColor);
          bBox.getChildren().add(iconLabel);
        }

        if (!isSpecialOptionSelected && actions[selectedIndex] == action) {
          aLabel.setTextFill(selectedColor);
        } else {
          aLabel.setTextFill(textColor);
        }
      }
    }

    // Update special options
    for (SpecialOption opt : SpecialOption.values()) {
      Label label = specialLabels.get(opt);
      if (isSpecialOptionSelected && selectedSpecial == opt) {
        label.setTextFill(selectedColor);
      } else {
        label.setTextFill(mutedColor);
      }
    }

    // Update hints
    updateHints();
  }

  private void updateHints() {
    VBox bottom = (VBox) rootPane.getBottom();
    bottom.getChildren().clear();

    Font hintFont = ctx.assets().getFont(FontKey.LABEL);
    Font iconFont = ctx.assets().getFont(FontKey.HINT);
    Color hintColor = ctx.assets().getColor(ColorKey.TEXT_HINT);

    HBox hintsRow = new HBox(40);
    hintsRow.setAlignment(Pos.CENTER);

    if (state == State.NAVIGATION) {
      hintsRow
          .getChildren()
          .addAll(
              UiFactory.createHint(
                  InputHintProvider.getIcon(KeyCode.UP) + InputHintProvider.getIcon(KeyCode.DOWN),
                  "Navigate",
                  iconFont,
                  hintFont,
                  hintColor),
              UiFactory.createHint(
                  InputHintProvider.getIconForAction(
                      InputAction.CONFIRM, ctx.settings().controls()),
                  "Select",
                  iconFont,
                  hintFont,
                  hintColor),
              UiFactory.createHint(
                  InputHintProvider.getIconForAction(InputAction.BACK, ctx.settings().controls()),
                  "Back",
                  iconFont,
                  hintFont,
                  hintColor));
    } else {
      hintsRow
          .getChildren()
          .add(
              UiFactory.createHint(
                  "", "Press any key to bind (ESC to cancel)", iconFont, hintFont, hintColor));
    }

    bottom.getChildren().add(hintsRow);
  }

  @Override
  public boolean supportsInputRepeat() {
    return true;
  }

  @Override
  public void onKeyPressed(InputAction action) {
    if (state == State.LISTENING) return;

    switch (action) {
      case UI_UP -> moveSelection(-1);
      case UI_DOWN -> moveSelection(1);
      case CONFIRM -> handleConfirm();
      case BACK -> navigateBack();
      default -> {}
    }
  }

  @Override
  public void onRawKeyPressed(KeyCode code) {
    if (state == State.LISTENING) {
      if (code == KeyCode.ESCAPE) {
        state = State.NAVIGATION;
        ctx.audio().playSound(AudioKey.CLICK);
      } else {
        bindKey(code);
      }
    }
  }

  @Override
  public boolean suppressActionBindings() {
    return state == State.LISTENING;
  }

  private void moveSelection(int delta) {
    ctx.audio().playSound(AudioKey.MOVE_SELECTION);

    int totalItems = actions.length + SpecialOption.values().length;
    int currentGlobalIndex =
        isSpecialOptionSelected ? actions.length + selectedSpecial.ordinal() : selectedIndex;

    int nextGlobalIndex = (currentGlobalIndex + delta + totalItems) % totalItems;

    if (nextGlobalIndex < actions.length) {
      isSpecialOptionSelected = false;
      selectedIndex = nextGlobalIndex;
    } else {
      isSpecialOptionSelected = true;
      selectedSpecial = SpecialOption.values()[nextGlobalIndex - actions.length];
    }
  }

  private void handleConfirm() {
    ctx.audio().playSound(AudioKey.CLICK);
    if (isSpecialOptionSelected) {
      switch (selectedSpecial) {
        case RESET_DEFAULTS -> {
          ctx.settings().controls().resetToDefaults();
          ctx.settings().save();
        }
        case BACK -> navigateBack();
      }
    } else {
      state = State.LISTENING;
    }
  }

  private void bindKey(KeyCode code) {
    InputAction action = actions[selectedIndex];
    ctx.settings().controls().unbindAll(action);
    ctx.settings().controls().rebind(code, action);
    ctx.settings().save();
    state = State.NAVIGATION;
    ctx.audio().playSound(AudioKey.CLICK);
  }

  private void navigateBack() {
    ctx.navigator().requestSwitch(new OptionsScreen());
  }
}
