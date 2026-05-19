package parcelpanic.screens;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
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
import parcelpanic.video.VideoManager;

public final class KickOverlay implements Screen {
  private final String message;
  private AppContext ctx;
  private BorderPane rootPane;
  private Color textColor;
  private Color selectedColor;

  public KickOverlay(String message) {
    this.message = message;
  }

  @Override
  public void enter(AppContext ctx) {
    this.ctx = ctx;
    this.textColor = ctx.assets().getColor(ColorKey.TEXT_LIGHT);
    this.selectedColor = ctx.assets().getColor(ColorKey.SUCCESS);

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
  public void fixedUpdate(double dtSeconds) {}

  @Override
  public void render(double alpha) {}

  private void buildUI() {
    Color surfaceBlack = ctx.assets().getColor(ColorKey.SURFACE_BLACK);

    rootPane = UiFactory.createBorderPane(VideoManager.LOGICAL_WIDTH, VideoManager.LOGICAL_HEIGHT);
    rootPane.setBackground(
        UiFactory.createOverlay(
                surfaceBlack, 0.85, VideoManager.LOGICAL_WIDTH, VideoManager.LOGICAL_HEIGHT)
            .getBackground());

    // Title
    Font titleFont = ctx.assets().getFont(FontKey.DISPLAY);
    Label title = UiFactory.createTitle("DISCONNECTED", titleFont, textColor);

    // Message
    Font msgFont = ctx.assets().getFont(FontKey.TITLE);
    Label msgLabel = UiFactory.createLabel(message, msgFont, textColor);
    msgLabel.setAlignment(Pos.CENTER);

    // Acknowledge Button
    Font btnFont = ctx.assets().getFont(FontKey.TITLE);
    Label ackButton = UiFactory.createLabel("Back to Main Menu", btnFont, selectedColor);
    ackButton.setStyle(
        "-fx-border-color: #00ff66; -fx-border-width: 2px; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-padding: 10px 24px; -fx-background-color: rgba(0, 255, 102, 0.1);");
    ackButton.setTranslateY(30);

    VBox centerContainer = new VBox(25, title, msgLabel, ackButton);
    centerContainer.setAlignment(Pos.CENTER);

    // Bottom container for hints
    Font hintFont = ctx.assets().getFont(FontKey.LABEL);
    Font iconFont = ctx.assets().getFont(FontKey.HINT);
    Color hintColor = ctx.assets().getColor(ColorKey.TEXT_HINT);

    Node selectHint =
        UiFactory.createHint(
            InputHintProvider.getIconForAction(InputAction.CONFIRM, ctx.settings().controls()),
            "Confirm",
            iconFont,
            hintFont,
            hintColor);

    VBox bottomContainer = new VBox(20, selectHint);
    bottomContainer.setAlignment(Pos.BOTTOM_CENTER);
    bottomContainer.setPadding(new Insets(0, 0, 60, 0));

    rootPane.setTop(new VBox());
    rootPane.setCenter(centerContainer);
    rootPane.setBottom(bottomContainer);
  }

  @Override
  public boolean supportsInputRepeat() {
    return false;
  }

  @Override
  public void onKeyPressed(InputAction action) {
    if (action == InputAction.CONFIRM) {
      ctx.audio().playSound(AudioKey.CLICK);
      ctx.navigator().pop();
      ctx.navigator().requestSwitch(new MenuScreen());
    }
  }
}
