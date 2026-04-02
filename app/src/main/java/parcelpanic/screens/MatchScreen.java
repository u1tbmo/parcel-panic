package parcelpanic.screens;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import parcelpanic.input.InputAction;
import parcelpanic.input.InputHintProvider;
import parcelpanic.media.AssetKeys.ColorKey;
import parcelpanic.media.AssetKeys.FontKey;
import parcelpanic.media.UiFactory;
import parcelpanic.screen.ContentScreen;
import parcelpanic.video.VideoManager;

public final class MatchScreen extends ContentScreen {
  private double roundSeconds;
  private Label timerLabel;
  private BorderPane rootPane;

  private Color textColor;
  private Color surfaceDark;

  @Override
  protected VideoManager.ViewportMode viewportMode() {
    return VideoManager.ViewportMode.FIXED_16_9;
  }

  @Override
  protected void onBeforeBuild() {
    this.roundSeconds = 180.0;
    this.textColor = ctx.assets().getColor(ColorKey.TEXT_LIGHT);
    this.surfaceDark = ctx.assets().getColor(ColorKey.SURFACE_DARK);
  }

  @Override
  protected Node createContent() {
    buildUI();
    return rootPane;
  }

  private void buildUI() {
    rootPane = UiFactory.createBorderPane(VideoManager.LOGICAL_WIDTH, VideoManager.LOGICAL_HEIGHT);
    rootPane.setBackground(
        UiFactory.createBackground(
                surfaceDark, VideoManager.LOGICAL_WIDTH, VideoManager.LOGICAL_HEIGHT)
            .getBackground());

    VBox centerContainer = createCenterContainer();
    VBox bottomContainer = createBottomContainer();

    rootPane.setCenter(centerContainer);
    rootPane.setBottom(bottomContainer);
  }

  private VBox createCenterContainer() {
    Font font = ctx.assets().getFont(FontKey.HEADLINE);
    Label status = UiFactory.createLabel("MATCH RUNNING", font, textColor);

    Font timerFont = ctx.assets().getFont(FontKey.TITLE);
    timerLabel = UiFactory.createLabel("Time: 180", timerFont, textColor);

    VBox container = new VBox(20);
    container.setAlignment(Pos.CENTER);
    container.getChildren().addAll(status, timerLabel);
    return container;
  }

  private VBox createBottomContainer() {
    Font hintFont = ctx.assets().getFont(FontKey.LABEL);
    Font iconFont = ctx.assets().getFont(FontKey.HINT);
    Color hintColor = ctx.assets().getColor(ColorKey.TEXT_HINT);

    Node pauseHint =
        UiFactory.createHint(
            InputHintProvider.getIconForAction(InputAction.PAUSE, ctx.settings().controls()),
            "Pause",
            iconFont,
            hintFont,
            hintColor);

    VBox container = new VBox(pauseHint);
    container.setAlignment(Pos.BOTTOM_CENTER);
    container.setPadding(new Insets(0, 0, 60, 0));
    return container;
  }

  @Override
  public void fixedUpdate(double dt) {
    roundSeconds -= dt;
    if (roundSeconds <= 0) {
      ctx.navigator().requestSwitch(new ResultsScreen(1234));
    }
  }

  @Override
  public void render(double alpha) {
    if (timerLabel != null) {
      timerLabel.setText("Time: " + Math.max(0, (int) roundSeconds));
    }
  }

  @Override
  public void onKeyPressed(InputAction action) {
    if (action == InputAction.PAUSE) {
      ctx.navigator().push(new PauseOverlay());
    }
  }
}
