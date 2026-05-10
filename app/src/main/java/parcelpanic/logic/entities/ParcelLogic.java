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

  private double x, y;
  private double vx, vy;

  private State currentState = State.AT_HUB;
  private Integer carrierId = null;
  private double remainingTime = MatchRules.MAX_PARCEL_TIME;
  private boolean isDamaged = false;
  private boolean isExpired = false;

  private double airTime = 0;

  public ParcelLogic(int id, int targetHouseId, double x, double y) {
    this.id = id;
    this.targetHouseId = targetHouseId;
    this.x = x;
    this.y = y;
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

    if (currentState == State.THROWN) {
      x += vx * dt;
      y += vy * dt;
      airTime -= dt;

      if (airTime <= 0) {
        land();
      }
    }

    return justExpired;
  }

  public void launch(double startX, double startY, double launchVx, double launchVy) {
    this.x = startX;
    this.y = startY;
    this.vx = launchVx;
    this.vy = launchVy;
    this.airTime = MatchRules.MAX_THROW_TIME;
    this.currentState = State.THROWN;
    this.carrierId = null;
  }

  public void pickup(int vehicleId) {
    this.currentState = State.CARRIED;
    this.carrierId = vehicleId;
  }

  public void land() {
    this.currentState = State.ON_GROUND;
    this.vx = 0;
    this.vy = 0;
  }

  public void updatePosition(double newX, double newY) {
    this.x = newX;
    this.y = newY;
  }

  public ParcelState state() {
    return new ParcelState(id, x, y, carrierId, remainingTime, isDamaged, targetHouseId);
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
