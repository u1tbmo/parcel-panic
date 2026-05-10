package parcelpanic.screen;

import java.util.ArrayDeque;
import java.util.Deque;
import javafx.scene.Node;
import parcelpanic.input.InputAction;
import parcelpanic.runtime.AppContext;

/// Manages screens, overlays, and switching
public final class ScreenManager {
  /// Global state of application
  private AppContext ctx;

  /// The current screen
  private Screen current;

  /// The stack of overlays over screens
  private final Deque<Screen> overlayStack = new ArrayDeque<>();

  /// The screen that will replace the current screen
  private Screen pendingScreen;

  public ScreenManager() {}

  public ScreenManager(AppContext ctx) {
    this.ctx = ctx;
  }

  /// Binds the global state of the application to the screen manager
  public void bindContext(AppContext ctx) {
    if (this.ctx != null) {
      throw new IllegalStateException("ScreenManager context is already bound.");
    }
    this.ctx = ctx;
  }

  /// Starts the screen manager
  public void start(Screen initial) {
    ensureContextBound();
    current = initial;
    current.enter(ctx);
    updateCanvas();
  }

  /// Request a switch to a screen
  public void requestSwitch(Screen next) {
    pendingScreen = next;
  }

  /// Push an overlay to the current screen
  public void push(Screen overlay) {
    overlay.enter(ctx);
    overlayStack.push(overlay);
    updateCanvas();
  }

  /// Remove the top-most overlay from the current screen
  public void pop() {
    if (!overlayStack.isEmpty()) {
      Screen top = overlayStack.pop();
      top.exit();
      updateCanvas();
    }
  }

  /// Updates the simulation of the active screen.
  ///
  /// Called every simulation tick.
  public void fixedUpdate(double dt) {
    switchScreens();
    Screen active = activeScreen();
    if (active != null) {
      active.fixedUpdate(dt);
    }
  }

  /// Renders a frame of the active screen.
  ///
  /// Called every frame.
  public void render(double alpha) {
    if (current != null) {
      current.render(alpha);
    }
    if (!overlayStack.isEmpty()) {
      overlayStack.peek().render(alpha);
    }
  }

  /// Routes action presses to the active screen.
  public void onActionPressed(InputAction action) {
    Screen active = activeScreen();
    if (active != null) {
      active.onKeyPressed(action);
    }
  }

  /// Routes action releases to the active screen.
  public void onActionReleased(InputAction action) {
    Screen active = activeScreen();
    if (active != null) {
      active.onKeyReleased(action);
    }
  }

  /// Routes raw key presses to the active screen.
  public void onRawKeyPressed(javafx.scene.input.KeyCode code) {
    Screen active = activeScreen();
    if (active != null) {
      active.onRawKeyPressed(code);
    }
  }

  /// Routes raw key releases to the active screen.
  public void onRawKeyReleased(javafx.scene.input.KeyCode code) {
    Screen active = activeScreen();
    if (active != null) {
      active.onRawKeyReleased(code);
    }
  }

  /// Returns the active screen
  private Screen activeScreen() {
    return overlayStack.isEmpty() ? current : overlayStack.peek();
  }

  /// Returns whether the active screen is suppressing action bindings
  public boolean isInputSuppressed() {
    Screen active = activeScreen();
    return active != null && active.suppressActionBindings();
  }

  public boolean supportsInputRepeat() {
    Screen active = activeScreen();
    return active != null && active.supportsInputRepeat();
  }

  /// Applies a switch if there is a requested screen switch
  private void switchScreens() {
    // If pending screen is null, then there is nothing to switch to.
    if (pendingScreen == null) {
      return;
    }

    ensureContextBound();

    // Remove all overlays
    while (!overlayStack.isEmpty()) {
      overlayStack.pop().exit();
    }

    // Exit the current screen
    if (current != null) {
      current.exit();
    }

    // Switch out the current screen
    current = pendingScreen;
    pendingScreen = null;
    current.enter(ctx);
    updateCanvas();
  }

  // Updates the root pane
  private void updateCanvas() {
    ctx.canvas().getChildren().clear();
    if (current != null) {
      Node root = current.getRoot();
      if (root != null) {
        ctx.canvas().getChildren().add(root);
      }
    }
    for (Screen overlay : overlayStack) {
      Node root = overlay.getRoot();
      if (root != null) {
        ctx.canvas().getChildren().add(root);
      }
    }
  }

  private void ensureContextBound() {
    if (ctx == null) {
      throw new IllegalStateException(
          "ScreenManager requires AppContext. Call bindContext() first.");
    }
  }
}
