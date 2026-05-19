package parcelpanic.logic.entities;

import parcelpanic.logic.MatchRules;
import parcelpanic.shared.PlayerIntent;
import parcelpanic.shared.VehicleState;

public class VehicleLogic {
  private final int id;

  // Position
  private double x;
  private double y;

  // Physics
  private double vx = 0;
  private double vy = 0;
  private double rotation = 0;

  private double dashCooldown = 0;
  private double pickupCooldown = 0;
  private boolean hasParcel = false;
  private boolean isDashing = false;
  private boolean isAccelerating = false;
  private VehicleState.PromptType prompt = VehicleState.PromptType.NONE;
  private int colorIndex = 1; // default to Red style 1

  // 0..1 interaction progress for the current prompt.
  private double interactProgress = 0.0;
  private String playerName = "Player";

  public VehicleLogic(int id, double x, double y) {
    this.id = id;
    this.x = x;
    this.y = y;
  }

  public void setHasParcel(boolean hasParcel) {
    this.hasParcel = hasParcel;
  }

  public boolean hasParcel() {
    return hasParcel;
  }

  public void triggerPickupCooldown() {
    this.pickupCooldown = MatchRules.PICKUP_COOLDOWN_TIME;
  }

  public void setPrompt(VehicleState.PromptType prompt) {
    this.prompt = prompt == null ? VehicleState.PromptType.NONE : prompt;
  }

  public boolean canPickup() {
    return pickupCooldown <= 0 && !hasParcel;
  }

  private double approach(double current, double target, double maxDelta) {
    return current < target
        ? Math.min(current + maxDelta, target)
        : Math.max(current - maxDelta, target);
  }

  public void update(double dt, PlayerIntent intent) {
    if (pickupCooldown > 0) {
      pickupCooldown -= dt;
    }
    isAccelerating = intent.up() || intent.down() || intent.left() || intent.right();

    double inputX = 0;
    double inputY = 0;

    if (intent.up()) {
      inputY -= 1;
      rotation = 0;
    }
    if (intent.down()) {
      inputY += 1;
      rotation = 180;
    }
    if (intent.left()) {
      inputX -= 1;
      rotation = 270;
    }
    if (intent.right()) {
      inputX += 1;
      rotation = 90;
    }

    // Normalize diagonal input so top speed is consistent.
    double len = Math.hypot(inputX, inputY);
    if (len > 1e-6) {
      inputX /= len;
      inputY /= len;
    }

    double targetVx = inputX * MatchRules.VEHICLE_MAX_SPEED;
    double targetVy = inputY * MatchRules.VEHICLE_MAX_SPEED;

    // Determine X acceleration/friction
    double accelX = (targetVx != 0) ? MatchRules.VEHICLE_ACCELERATION : MatchRules.VEHICLE_FRICTION;
    // If over top speed (e.g. from dashing) and still holding the same direction, use friction to
    // drag it back down to top speed
    if (targetVx != 0
        && Math.abs(vx) > MatchRules.VEHICLE_MAX_SPEED
        && Math.signum(vx) == Math.signum(targetVx)) {
      accelX = MatchRules.VEHICLE_FRICTION;
    }
    vx = approach(vx, targetVx, accelX * dt);

    // Determine Y acceleration/friction
    double accelY = (targetVy != 0) ? MatchRules.VEHICLE_ACCELERATION : MatchRules.VEHICLE_FRICTION;
    if (targetVy != 0
        && Math.abs(vy) > MatchRules.VEHICLE_MAX_SPEED
        && Math.signum(vy) == Math.signum(targetVy)) {
      accelY = MatchRules.VEHICLE_FRICTION;
    }
    vy = approach(vy, targetVy, accelY * dt);

    // Handle dash
    isDashing = false;
    if (dashCooldown > 0) {
      dashCooldown -= dt;
    } else if (intent.dash()) {
      double currentSpeed = Math.hypot(vx, vy);
      if (currentSpeed > 0.1) {
        vx += (vx / currentSpeed) * MatchRules.DASH_FORCE;
        vy += (vy / currentSpeed) * MatchRules.DASH_FORCE;
      } else {
        if (rotation == 0) vy -= MatchRules.DASH_FORCE;
        else if (rotation == 180) vy += MatchRules.DASH_FORCE;
        else if (rotation == 270) vx -= MatchRules.DASH_FORCE;
        else if (rotation == 90) vx += MatchRules.DASH_FORCE;
      }
      dashCooldown = MatchRules.DASH_COOLDOWN_TIME;
      isDashing = true;
      isAccelerating = true;
    }

    // Update position
    x += vx * dt;
    y += vy * dt;

    // Determine visual and hitbox rotation based on momentum
    if (Math.abs(vx) > 1.0 || Math.abs(vy) > 1.0) {
      if (Math.abs(vx) > Math.abs(vy)) {
        rotation = (vx > 0) ? 90 : 270;
      } else {
        rotation = (vy > 0) ? 180 : 0;
      }
    }
  }

  public void setPlayerName(String name) {
    this.playerName = name != null ? name : "Player";
  }

  public VehicleState state() {
    return new VehicleState(
        id,
        x,
        y,
        vx,
        vy,
        rotation,
        isDashing,
        isAccelerating,
        prompt,
        colorIndex,
        interactProgress,
        playerName);
  }

  public void setInteractProgress(double interactProgress) {
    this.interactProgress = Math.max(0.0, Math.min(1.0, interactProgress));
  }

  public int getColorIndex() {
    return colorIndex;
  }

  public void setColorIndex(int colorIndex) {
    this.colorIndex = colorIndex;
  }

  public int id() {
    return id;
  }

  public double x() {
    return x;
  }

  public double y() {
    return y;
  }

  public void setX(double x) {
    this.x = x;
  }

  public void setY(double y) {
    this.y = y;
  }

  public void stopVelocityX() {
    this.vx = 0;
  }

  public void stopVelocityY() {
    this.vy = 0;
  }
}
