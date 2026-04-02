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
import parcelpanic.input.InputBindings;
import parcelpanic.input.InputHintProvider;
import parcelpanic.media.AssetKeys.AudioKey;
import parcelpanic.media.AssetKeys.ColorKey;
import parcelpanic.media.AssetKeys.FontKey;
import parcelpanic.media.UiFactory;
import parcelpanic.screen.ContentScreen;
import parcelpanic.screen.SelectionCursor;
import parcelpanic.util.SmoothedValue;
import parcelpanic.video.DisplayMode;
import parcelpanic.video.Resolution;
import parcelpanic.video.VideoManager;

public final class OptionsScreen extends ContentScreen {
  private enum Option {
    RESOLUTION,
    DISPLAY_MODE,
    MASTER_VOLUME,
    KEYBINDINGS,
    BACK,
    APPLY
  }

  private static final Option[] SELECTABLE_ALL = {
    Option.RESOLUTION, Option.DISPLAY_MODE, Option.MASTER_VOLUME, Option.KEYBINDINGS, Option.BACK, Option.APPLY
  };
  private static final Option[] SELECTABLE_BORDERLESS = {
    Option.DISPLAY_MODE, Option.MASTER_VOLUME, Option.KEYBINDINGS, Option.BACK, Option.APPLY
  };

  private Option selectedOption = Option.RESOLUTION;
  private int resolutionIndex;

  // Temporary settings (not applied until user clicks Apply)
  private Resolution pendingResolution;
  private DisplayMode pendingDisplayMode;
  private double pendingVolume;

  // Available resolutions filtered by screen size
  private Resolution[] availableResolutions;

  private final Map<Option, Label> optionLabels = new EnumMap<>(Option.class);
  private final Map<Option, SmoothedValue> offsets = new EnumMap<>(Option.class);

  private BorderPane rootPane;

  @Override
  protected VideoManager.ViewportMode viewportMode() {
    return VideoManager.ViewportMode.FULL_WINDOW;
  }

  @Override
  protected void onBeforeBuild() {
    // Get available resolutions for this screen
    availableResolutions = Resolution.getAvailablePresets();

    // Load current settings
    pendingResolution = ctx.settings().video().resolution();
    pendingDisplayMode = ctx.settings().video().displayMode();
    pendingVolume = ctx.settings().audio().masterVolume();

    // Keep pending resolution and visible index in sync.
    syncResolutionSelection();
    selectedOption = getSelectableOptions()[0];

    // Initialize animation offsets
    for (Option opt : Option.values()) {
      offsets.put(opt, new SmoothedValue(0.0, 0.5));
    }
  }

  @Override
  protected Node createContent() {
    buildUI();
    return rootPane;
  }

  @Override
  public void fixedUpdate(double dtSeconds) {
    for (Option opt : Option.values()) {
      double target = (opt == selectedOption) ? 12.0 : 0.0;
      offsets.get(opt).update(target);
    }
  }

  @Override
  public void render(double alpha) {
    // Update option display
    updateOptionsDisplay();

    // Apply smooth offsets
    for (Map.Entry<Option, Label> entry : optionLabels.entrySet()) {
      entry.getValue().setTranslateX(offsets.get(entry.getKey()).get(alpha));
    }
  }

  private void buildUI() {
    // Create background
    rootPane = UiFactory.createBorderPane(VideoManager.LOGICAL_WIDTH, VideoManager.LOGICAL_HEIGHT);
    rootPane.setMinSize(VideoManager.LOGICAL_WIDTH, VideoManager.LOGICAL_HEIGHT);
    rootPane.setMaxSize(VideoManager.LOGICAL_WIDTH, VideoManager.LOGICAL_HEIGHT);

    initOptionLabels();

    VBox topContainer = createTopContainer();
    VBox optionsContainer = createOptionsContainer();
    VBox buttonContainer = createButtonContainer();
    VBox bottomContainer = createBottomContainer();

    VBox centerContainer = new VBox(40);
    centerContainer.getChildren().addAll(optionsContainer, buttonContainer);
    centerContainer.setAlignment(Pos.CENTER_LEFT);

    rootPane.setTop(topContainer);
    rootPane.setCenter(centerContainer);
    rootPane.setBottom(bottomContainer);

    updateOptionsDisplay();
  }

  private void handleConfirm() {
    switch (selectedOption) {
      case KEYBINDINGS -> navigateToKeybindings();
      case BACK -> navigateBackToMenu();
      case APPLY -> applySettings();
      default -> {}
    }
  }

  private void navigateToKeybindings() {
    ctx.navigator().requestSwitch(new KeybindingsScreen());
  }

  private void navigateBackToMenu() {
    ctx.navigator().requestSwitch(new MenuScreen());
  }

  private void initOptionLabels() {
    Font optionFont = ctx.assets().getFont(FontKey.TITLE);
    Font buttonFont = ctx.assets().getFont(FontKey.TITLE);
    Color textColor = ctx.assets().getColor(ColorKey.TEXT_LIGHT);
    Color mutedColor = ctx.assets().getColor(ColorKey.TEXT_MUTED);

    optionLabels.put(Option.RESOLUTION, UiFactory.createLabel("", optionFont, textColor));
    optionLabels.put(Option.DISPLAY_MODE, UiFactory.createLabel("", optionFont, textColor));
    optionLabels.put(Option.MASTER_VOLUME, UiFactory.createLabel("", optionFont, textColor));
    optionLabels.put(
        Option.KEYBINDINGS, UiFactory.createLabel("Keybindings", optionFont, textColor));
    optionLabels.put(Option.BACK, UiFactory.createLabel("Back", buttonFont, mutedColor));
    optionLabels.put(Option.APPLY, UiFactory.createLabel("Apply", buttonFont, mutedColor));
  }

  private VBox createTopContainer() {
    Font titleFont = ctx.assets().getFont(FontKey.HEADLINE);
    Label title =
        UiFactory.createTitle("Options", titleFont, ctx.assets().getColor(ColorKey.TEXT_LIGHT));
    VBox topContainer = new VBox(title);
    topContainer.setAlignment(Pos.TOP_CENTER);
    topContainer.setPadding(new Insets(80, 0, 0, 0));
    return topContainer;
  }

  private VBox createOptionsContainer() {
    VBox optionsContainer = new VBox(20);
    optionsContainer.setAlignment(Pos.CENTER_LEFT);
    optionsContainer
        .getChildren()
        .addAll(
            optionLabels.get(Option.RESOLUTION),
            optionLabels.get(Option.DISPLAY_MODE),
            optionLabels.get(Option.MASTER_VOLUME),
            optionLabels.get(Option.KEYBINDINGS));
    optionsContainer.setPadding(new Insets(0, 0, 0, 100));
    return optionsContainer;
  }

  private VBox createButtonContainer() {
    VBox buttonContainer = new VBox(20);
    buttonContainer.setAlignment(Pos.CENTER_LEFT);
    buttonContainer
        .getChildren()
        .addAll(optionLabels.get(Option.BACK), optionLabels.get(Option.APPLY));
    buttonContainer.setPadding(new Insets(0, 0, 0, 100));
    return buttonContainer;
  }

  private VBox createBottomContainer() {
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
            "Change",
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

    Node backHint =
        UiFactory.createHint(
            InputHintProvider.getIconForAction(InputAction.BACK, ctx.settings().controls()),
            "Back",
            iconFont,
            hintFont,
            hintColor);

    HBox hintsRow = new HBox(40, navigateHint, changeHint, selectHint, backHint);
    hintsRow.setAlignment(Pos.CENTER);

    VBox bottomContainer = new VBox(hintsRow);
    bottomContainer.setAlignment(Pos.BOTTOM_CENTER);
    bottomContainer.setPadding(new Insets(0, 0, 60, 0));
    return bottomContainer;
  }

  private void updateOptionsDisplay() {
    boolean isBorderless = isBorderlessMode();
    Color selectedColor = ctx.assets().getColor(ColorKey.SUCCESS);
    Color textColor = ctx.assets().getColor(ColorKey.TEXT_LIGHT);
    Color mutedColor = ctx.assets().getColor(ColorKey.TEXT_MUTED);
    Color disabledColor = ctx.assets().getColor(ColorKey.TEXT_DISABLED);

    Label resolutionLabel = optionLabels.get(Option.RESOLUTION);
    Label displayModeLabel = optionLabels.get(Option.DISPLAY_MODE);
    Label volumeLabel = optionLabels.get(Option.MASTER_VOLUME);
    Label keybindingsLabel = optionLabels.get(Option.KEYBINDINGS);
    Label backLabel = optionLabels.get(Option.BACK);
    Label applyLabel = optionLabels.get(Option.APPLY);

    // Resolution label
    if (isBorderless) {
      Resolution nativeRes = ctx.video().getNativeResolution();
      resolutionLabel.setText("Resolution: " + nativeRes.displayName() + " (Native)");
      resolutionLabel.setTextFill(disabledColor);
    } else {
      resolutionLabel.setText("Resolution: " + availableResolutions[resolutionIndex].displayName());
      resolutionLabel.setTextFill(selectedOption == Option.RESOLUTION ? selectedColor : textColor);
    }

    // Display Mode label
    displayModeLabel.setText("Display Mode: " + pendingDisplayMode.getDisplayName());
    displayModeLabel.setTextFill(selectedOption == Option.DISPLAY_MODE ? selectedColor : textColor);

    // Master Volume label
    volumeLabel.setText(String.format("Master Volume: %d%%", (int)(pendingVolume * 100)));
    volumeLabel.setTextFill(selectedOption == Option.MASTER_VOLUME ? selectedColor : textColor);

    // Keybindings
    keybindingsLabel.setTextFill(selectedOption == Option.KEYBINDINGS ? selectedColor : textColor);

    // Back button
    backLabel.setTextFill(selectedOption == Option.BACK ? selectedColor : mutedColor);

    // Apply button
    applyLabel.setTextFill(selectedOption == Option.APPLY ? selectedColor : mutedColor);
  }

  @Override
  public boolean supportsInputRepeat() {
    return true;
  }

  @Override
  public void onKeyPressed(InputAction action) {
    switch (action) {
      case UI_UP -> {
        moveSelection(-1);
        ctx.audio().playSound(AudioKey.MOVE_SELECTION);
      }
      case UI_DOWN -> {
        moveSelection(1);
        ctx.audio().playSound(AudioKey.MOVE_SELECTION);
      }
      case UI_LEFT -> {
        handleHorizontalInput(-1);
        ctx.audio().playSound(AudioKey.MOVE_SELECTION);
      }
      case UI_RIGHT -> {
        handleHorizontalInput(1);
        ctx.audio().playSound(AudioKey.MOVE_SELECTION);
      }
      case CONFIRM -> {
        handleConfirm();
        ctx.audio().playSound(AudioKey.CLICK);
      }
      case BACK -> {
        navigateBackToMenu();
        ctx.audio().playSound(AudioKey.CLICK);
      }
      default -> {}
    }
  }

  private boolean isBorderlessMode() {
    return pendingDisplayMode == DisplayMode.BORDERLESS;
  }

  private Option[] getSelectableOptions() {
    if (isBorderlessMode()) {
      return SELECTABLE_BORDERLESS;
    }
    return SELECTABLE_ALL;
  }

  private void moveSelection(int delta) {
    selectedOption = SelectionCursor.move(getSelectableOptions(), selectedOption, delta);
  }

  private void handleHorizontalInput(int delta) {
    if (selectedOption == Option.RESOLUTION) {
      if (!isBorderlessMode()) {
        cycleResolution(delta);
      }
      return;
    }

    if (selectedOption == Option.DISPLAY_MODE) {
      cycleDisplayMode(delta);
      return;
    }

    if (selectedOption == Option.MASTER_VOLUME) {
      adjustVolume(delta);
    }
  }

  private void cycleResolution(int delta) {
    resolutionIndex =
        SelectionCursor.wrapIndex(resolutionIndex, delta, availableResolutions.length);
    pendingResolution = availableResolutions[resolutionIndex];
  }

  private void cycleDisplayMode(int delta) {
    DisplayMode[] modes = DisplayMode.values();
    int nextIndex = (pendingDisplayMode.ordinal() + delta + modes.length) % modes.length;
    pendingDisplayMode = modes[nextIndex];
    syncResolutionSelection();

    if (isBorderlessMode() && selectedOption == Option.RESOLUTION) {
      selectedOption = Option.DISPLAY_MODE;
    }
  }

  private void adjustVolume(int delta) {
    // Prevent "audio breaking" by ignoring input if both left and right are held
    if (ctx.input().isPressed(InputAction.UI_LEFT) && ctx.input().isPressed(InputAction.UI_RIGHT)) {
      return;
    }

    // Acceleration logic: increase step size the longer the key is held
    double heldTime = (delta < 0) ? ctx.input().getHeldTime(InputAction.UI_LEFT)
                                 : ctx.input().getHeldTime(InputAction.UI_RIGHT);
    
    double step = 0.01; // Base 1%
    if (heldTime > 2.0) {
      step = 0.10; // 10% skips after 2s
    } else if (heldTime > 1.0) {
      step = 0.05; // 5% skips after 1s
    } else if (heldTime > 0.5) {
      step = 0.02; // 2% skips after 0.5s
    }

    pendingVolume = Math.clamp(pendingVolume + delta * step, 0.0, 1.0);
    ctx.audio().setVolume(pendingVolume);
  }

  private void syncResolutionSelection() {
    resolutionIndex = findResolutionIndex(pendingResolution);
    pendingResolution = availableResolutions[resolutionIndex];
  }

  private void applySettings() {
    // Update settings
    ctx.settings().video().setResolution(pendingResolution);
    ctx.settings().video().setDisplayMode(pendingDisplayMode);
    ctx.settings().audio().setMasterVolume(pendingVolume);
    ctx.settings().save();

    // Apply to video manager
    ctx.video().applySettings(ctx.settings().video());

    // Update input handlers for new scene
    InputBindings.bindScene(
        ctx.video().getScene(), ctx.input(), ctx.navigator(), ctx.settings().controls());

    // Go back to menu
    ctx.navigator().requestSwitch(new MenuScreen());
  }

  private int findResolutionIndex(Resolution resolution) {
    for (int i = 0; i < availableResolutions.length; i++) {
      if (availableResolutions[i].width() == resolution.width()
          && availableResolutions[i].height() == resolution.height()) {
        return i;
      }
    }
    // Default to middle resolution or first if not found
    return Math.min(availableResolutions.length / 2, availableResolutions.length - 1);
  }
}
