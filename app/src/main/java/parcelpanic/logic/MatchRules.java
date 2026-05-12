package parcelpanic.logic;

import parcelpanic.logic.entities.ParcelLogic;

public class MatchRules {
  // World Layout
  public static final int TILE_SIZE = 40;

  // Physics - Vehicle
  public static final double VEHICLE_MAX_SPEED = 450.0;
  public static final double VEHICLE_ACCELERATION = VEHICLE_MAX_SPEED / (9.0 / 60.0);
  public static final double VEHICLE_FRICTION = VEHICLE_MAX_SPEED / (4.0 / 60.0);

  public static final double VEHICLE_SCALE = 40.0 / 22.0;

  public static final double VEHICLE_V_WIDTH = 13.0 * VEHICLE_SCALE;
  public static final double VEHICLE_V_HEIGHT = 20.0 * VEHICLE_SCALE;
  public static final double VEHICLE_V_OFFSET_X = -1.5 * VEHICLE_SCALE;
  public static final double VEHICLE_V_OFFSET_Y = -1.0 * VEHICLE_SCALE;

  public static final double VEHICLE_H_WIDTH = 21.0 * VEHICLE_SCALE;
  public static final double VEHICLE_H_HEIGHT = 14.0 * VEHICLE_SCALE;
  public static final double VEHICLE_H_OFFSET_X = -0.5 * VEHICLE_SCALE;
  public static final double VEHICLE_H_OFFSET_Y = 2.0 * VEHICLE_SCALE;

  public static final double DASH_FORCE = 900.0;
  public static final double DASH_COOLDOWN_TIME = 0.6;

  // Physics - Items
  public static final double THROW_SPEED = 600.0;
  public static final double PARCEL_FRICTION = 0.96;
  public static final double PARCEL_BOUNCE = 0.6;
  public static final double PARCEL_MIN_SPEED = 30.0;
  public static final double PARCEL_GRAVITY = 800.0;
  public static final double PARCEL_INITIAL_Z = 20.0;
  public static final double PARCEL_LAUNCH_VZ = 200.0;
  public static final double PARCEL_BOUNCE_Z = 0.5;
  public static final double PARCEL_SIZE = 24.0;
  public static final double INTERACT_RANGE = 32.0;

  // Timers & Lifecycle
  public static final double MATCH_DURATION = 180.0;
  public static final double INTERACT_TIME_REQUIRED = 0.6;
  public static final double PICKUP_COOLDOWN_TIME = 0.5;
  public static final double MAX_PARCEL_TIME = 30.0;

  // Scoring & Penalties
  public static final double MAX_UNHAPPINESS = 100.0;
  public static final double PENALTY_PER_EXPIRATION = 15.0;
  public static final double SCORE_PER_DELIVERY = 100.0;

  // Spawning
  public static final double PARCEL_SPAWN_INTERVAL = 7.5;
  public static final int MAX_PARCELS_ON_SCREEN = 10;

  public static double calculatePenalty(double currentUnhappiness) {
    return Math.min(MAX_UNHAPPINESS, currentUnhappiness + PENALTY_PER_EXPIRATION);
  }

  public static boolean isGameOver(double currentUnhappiness, double remainingTime) {
    return currentUnhappiness >= MAX_UNHAPPINESS || remainingTime <= 0;
  }

  public static double calculateDeliveryScore(double remainingTimeOnParcel) {
    double speedBonus = remainingTimeOnParcel * 2.0;
    return SCORE_PER_DELIVERY + speedBonus;
  }

  public static boolean isValidDelivery(ParcelLogic parcel, int zoneId) {
    return parcel.targetHouseId() == zoneId && parcel.currentState() == ParcelLogic.State.CARRIED;
  }
}
