package parcelpanic.view;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import parcelpanic.util.SmoothedValue;

/**
 * A simple banner text that slides from offscreen-left to center, then offscreen-right.
 *
 * <p>Update using {@link #fixedUpdate(double)} and apply interpolated transforms in {@link
 * #render(double)}.
 */
public final class SlidingCenterText extends StackPane {
  private enum Phase {
    IDLE,
    ENTER,
    HOLD,
    EXIT
  }

  private final Label label;

  private SmoothedValue x;
  private SmoothedValue opacity;

  private Phase phase = Phase.IDLE;
  private double phaseTime = 0.0;

  private double offscreenX = 1000.0;
  private double enterSeconds = 0.18;
  private double holdSeconds = 0.32;
  private double exitSeconds = 0.18;
  private double smoothing = 0.28;

  public SlidingCenterText(Font font, Color color) {
    this.label = new Label("");
    this.label.setFont(font);
    this.label.setTextFill(color);
    this.label.setEffect(new DropShadow(8.0, Color.rgb(0, 0, 0, 0.55)));

    setAlignment(Pos.CENTER);
    setPickOnBounds(false);
    getChildren().add(label);

    // Start hidden.
    setVisible(false);
    setManaged(false);

    this.x = new SmoothedValue(-offscreenX, smoothing);
    this.opacity = new SmoothedValue(0.0, smoothing);
  }

  public void setOffscreenX(double offscreenX) {
    this.offscreenX = offscreenX;
  }

  public void setTimings(double enterSeconds, double holdSeconds, double exitSeconds) {
    this.enterSeconds = enterSeconds;
    this.holdSeconds = holdSeconds;
    this.exitSeconds = exitSeconds;
  }

  public void setSmoothing(double smoothing) {
    this.smoothing = smoothing;
  }

  public boolean isPlaying() {
    return phase != Phase.IDLE;
  }

  public void play(String text) {
    label.setText(text);

    // Reset animation state.
    phase = Phase.ENTER;
    phaseTime = 0.0;
    x = new SmoothedValue(-offscreenX, smoothing);
    opacity = new SmoothedValue(0.0, smoothing);

    setTranslateX(-offscreenX);
    setOpacity(0.0);
    setVisible(true);
    setManaged(true);
  }

  public void fixedUpdate(double dt) {
    if (phase == Phase.IDLE) {
      return;
    }

    phaseTime += dt;

    double targetX;
    double targetOpacity;

    switch (phase) {
      case ENTER -> {
        double t = Math.min(1.0, phaseTime / Math.max(enterSeconds, 1e-6));
        targetX = lerp(-offscreenX, 0.0, t);
        targetOpacity = t;
        if (phaseTime >= enterSeconds) {
          phase = Phase.HOLD;
          phaseTime = 0.0;
        }
      }
      case HOLD -> {
        targetX = 0.0;
        targetOpacity = 1.0;
        if (phaseTime >= holdSeconds) {
          phase = Phase.EXIT;
          phaseTime = 0.0;
        }
      }
      case EXIT -> {
        double t = Math.min(1.0, phaseTime / Math.max(exitSeconds, 1e-6));
        targetX = lerp(0.0, offscreenX, t);
        targetOpacity = 1.0 - t;
        if (phaseTime >= exitSeconds) {
          phase = Phase.IDLE;
          phaseTime = 0.0;
          setVisible(false);
          setManaged(false);
        }
      }
      default -> {
        targetX = 0.0;
        targetOpacity = 0.0;
      }
    }

    x.update(targetX);
    opacity.update(targetOpacity);
  }

  public void render(double alpha) {
    if (!isVisible()) {
      return;
    }
    setTranslateX(x.get(alpha));
    setOpacity(opacity.get(alpha));
  }

  private static double lerp(double a, double b, double t) {
    return a + (b - a) * t;
  }
}
