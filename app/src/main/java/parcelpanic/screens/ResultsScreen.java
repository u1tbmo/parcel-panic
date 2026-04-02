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

public final class ResultsScreen extends ContentScreen {
  private final int score;
  private BorderPane rootPane;

  private Color textColor;
  private Color surfaceBlack;

  public ResultsScreen(int score) {
    this.score = score;
  }

  @Override
  protected VideoManager.ViewportMode viewportMode() {
    return VideoManager.ViewportMode.FULL_WINDOW;
  }

  @Override
  protected void onBeforeBuild() {
    this.textColor = ctx.assets().getColor(ColorKey.TEXT_LIGHT);
    this.surfaceBlack = ctx.assets().getColor(ColorKey.SURFACE_BLACK);
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
                surfaceBlack, VideoManager.LOGICAL_WIDTH, VideoManager.LOGICAL_HEIGHT)
            .getBackground());

    VBox topContainer = createTopContainer();
    VBox centerContainer = createCenterContainer();
    VBox bottomContainer = createBottomContainer();

    rootPane.setTop(topContainer);
    rootPane.setCenter(centerContainer);
    rootPane.setBottom(bottomContainer);
  }

  private VBox createTopContainer() {
    Font font = ctx.assets().getFont(FontKey.HEADLINE);
    Label title = UiFactory.createTitle("RESULTS", font, textColor);
    VBox container = new VBox(title);
    container.setAlignment(Pos.TOP_CENTER);
    container.setPadding(new Insets(80, 0, 0, 0));
    return container;
  }

  private VBox createCenterContainer() {
    Font scoreFont = ctx.assets().getFont(FontKey.DISPLAY);
    Label scoreLabel = UiFactory.createLabel("Score: " + score, scoreFont, textColor);

    VBox container = new VBox(30);
    container.setAlignment(Pos.CENTER);
    container.getChildren().add(scoreLabel);
    return container;
  }

  private VBox createBottomContainer() {
    Font hintFont = ctx.assets().getFont(FontKey.LABEL);
    Font iconFont = ctx.assets().getFont(FontKey.HINT);
    Color hintColor = ctx.assets().getColor(ColorKey.TEXT_HINT);

    Node confirmHint =
        UiFactory.createHint(
            InputHintProvider.getIconForAction(InputAction.CONFIRM, ctx.settings().controls()),
            "Main Menu",
            iconFont,
            hintFont,
            hintColor);

    VBox container = new VBox(confirmHint);
    container.setAlignment(Pos.BOTTOM_CENTER);
    container.setPadding(new Insets(0, 0, 60, 0));
    return container;
  }

  @Override
  public void fixedUpdate(double dt) {}

  @Override
  public void render(double alpha) {
    // Static screen, no updates needed
  }

  @Override
  public void onKeyPressed(InputAction action) {
    if (action == InputAction.CONFIRM) {
      ctx.navigator().requestSwitch(new MenuScreen());
    }
  }
}
