package parcelpanic.screen;

import javafx.scene.Node;
import parcelpanic.runtime.AppContext;
import parcelpanic.video.VideoManager;

/// A screen with content
public abstract class ContentScreen implements Screen {
  protected AppContext ctx;
  private Node rootNode;

  @Override
  public final void enter(AppContext ctx) {
    this.ctx = ctx;
    ctx.video().setViewportMode(viewportMode());
    onBeforeBuild();
    rootNode = createContent();
    onAfterShow();
  }

  @Override
  public void exit() {
    onBeforeExit();
    rootNode = null;
  }

  @Override
  public Node getRoot() {
    return rootNode;
  }

  @Override
  public void render(double alpha) {}

  protected void onBeforeBuild() {}

  protected void onAfterShow() {}

  protected void onBeforeExit() {}

  protected abstract VideoManager.ViewportMode viewportMode();

  protected abstract Node createContent();
}
