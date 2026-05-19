package parcelpanic.logic.entities;

import parcelpanic.logic.MatchRules;
import parcelpanic.shared.ParcelState;

public class ParcelLogic {
  public enum State {
    AT_HUB,
    CARRIED,
    THROWN,
    ON_GROUND
  }

  private final int id;
  private final int targetHouseId;

  private double x, y, z;
  private double vx, vy, vz;

  private State currentState = State.AT_HUB;
  private Integer carrierId = null;
  private double remainingTime = MatchRules.MAX_PARCEL_TIME;
  private boolean isDamaged = false;
  private boolean isExpired = false;

  public ParcelLogic(int id, int targetHouseId, double x, double y) {
    this.id = id;
    this.targetHouseId = targetHouseId;
    this.x = x;
    this.y = y;
    this.z = 0;
  }

  public boolean update(double dt) {
    boolean justExpired = false;

    if (!isExpired) {
      remainingTime -= dt;
      if (remainingTime <= 0) {
        remainingTime = 0;
        isExpired = true;
        justExpired = true;
      }
    }

    if (currentState == State.THROWN || currentState == State.ON_GROUND) {
      if (z > 0 || vz != 0) {
        z += vz * dt;
        vz -= MatchRules.PARCEL_GRAVITY * dt;

        if (z <= 0) {
          z = 0;
          if (Math.abs(vz) > 50) {
            vz = -vz * MatchRules.PARCEL_BOUNCE_Z;
          } else {
            vz = 0;
          }
        }
      }
    }

    if (currentState == State.THROWN) {
      // Apply friction (deceleration)
      vx *= Math.pow(MatchRules.PARCEL_FRICTION, dt * 60);
      vy *= Math.pow(MatchRules.PARCEL_FRICTION, dt * 60);

      x += vx * dt;
      y += vy * dt;

      // Stop sliding if speed is very low
      if (Math.hypot(vx, vy) < MatchRules.PARCEL_MIN_SPEED && z == 0) {
        land();
      }
    }

    return justExpired;
  }

  public boolean isStoppedOnGround() {
    return currentState == State.THROWN
        && z == 0
        && Math.hypot(vx, vy) < MatchRules.PARCEL_MIN_SPEED;
  }

  public void launch(double startX, double startY, double launchVx, double launchVy) {
    this.x = startX;
    this.y = startY;
    this.z = MatchRules.PARCEL_INITIAL_Z;
    this.vx = launchVx;
    this.vy = launchVy;
    this.vz = MatchRules.PARCEL_LAUNCH_VZ;
    this.currentState = State.THROWN;
    this.carrierId = null;
  }

  public void pickup(int vehicleId) {
    this.currentState = State.CARRIED;
    this.carrierId = vehicleId;
    this.z = MatchRules.PARCEL_INITIAL_Z;
    this.vz = 0;
  }

  public void land() {
    this.currentState = State.ON_GROUND;
    this.vx = 0;
    this.vy = 0;
  }

  public void drop() {
    this.currentState = State.ON_GROUND;
    this.carrierId = null;
    this.vx = 0;
    this.vy = 0;
    this.vz = 0;
    this.z = 0;
  }

  public void bounceX() {
    this.vx = -this.vx * MatchRules.PARCEL_BOUNCE;
  }

  public void bounceY() {
    this.vy = -this.vy * MatchRules.PARCEL_BOUNCE;
  }

  public void setX(double x) {
    this.x = x;
  }

  public void setY(double y) {
    this.y = y;
  }

  public void updatePosition(double newX, double newY) {
    this.x = newX;
    this.y = newY;
    this.z = MatchRules.PARCEL_INITIAL_Z;
  }

  public ParcelState state() {
    return new ParcelState(id, x, y, z, carrierId, remainingTime, isDamaged, targetHouseId);
  }

  public int id() {
    return id;
  }

  public State currentState() {
    return currentState;
  }

  public Integer carrierId() {
    return carrierId;
  }

  public int targetHouseId() {
    return targetHouseId;
  }

  public double remainingTime() {
    return remainingTime;
  }

  public boolean isExpired() {
    return isExpired;
  }

  public double x() {
    return x;
  }

  public double y() {
    return y;
  }
}
