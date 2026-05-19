package parcelpanic.screens;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane; 
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import parcelpanic.input.InputAction;
import parcelpanic.input.InputHintProvider;
import parcelpanic.media.AssetKeys.ColorKey;
import parcelpanic.media.AssetKeys.FontKey;
import parcelpanic.media.AssetKeys.ImageKey; 
import parcelpanic.media.UiFactory;
import parcelpanic.screen.ContentScreen;
import parcelpanic.video.VideoManager;

public final class ResultsScreen extends ContentScreen {
  private final int score;
  
  private StackPane rootContainer;
  private BorderPane rootPane;

  private Color textColor;

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
  }

  @Override
  protected Node createContent() {
    buildUI();
    return rootContainer;
  }

  private void buildUI() {
    rootContainer = new StackPane();
    rootContainer.setPrefSize(VideoManager.LOGICAL_WIDTH, VideoManager.LOGICAL_HEIGHT);

    Image backgroundImage = ctx.assets().getImage(ImageKey.MENU_RESULTS);
    if (backgroundImage != null) {
      ImageView bgImageView = new ImageView(backgroundImage);
      bgImageView.setFitWidth(VideoManager.LOGICAL_WIDTH);
      bgImageView.setFitHeight(VideoManager.LOGICAL_HEIGHT);
      bgImageView.setPreserveRatio(false);
      bgImageView.setSmooth(false); 
      
      rootContainer.getChildren().add(bgImageView);
    }


    rootPane = UiFactory.createBorderPane(VideoManager.LOGICAL_WIDTH, VideoManager.LOGICAL_HEIGHT);
    rootPane.setBackground(null);

    VBox topContainer = createTopContainer();
    VBox centerContainer = createCenterContainer();
    VBox bottomContainer = createBottomContainer();

    rootPane.setTop(topContainer);
    rootPane.setCenter(centerContainer);
    rootPane.setBottom(bottomContainer);


    rootContainer.getChildren().add(rootPane);
  }

  private VBox createTopContainer() {
    Font font = ctx.assets().getFont(FontKey.HEADLINE);
    Label title = UiFactory.createTitle("", font, textColor);
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