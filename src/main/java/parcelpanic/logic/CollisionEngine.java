package parcelpanic.logic;

import parcelpanic.logic.entities.ParcelLogic;
import parcelpanic.logic.entities.VehicleLogic;
import parcelpanic.world.TileMap;

public class CollisionEngine {

  public static void resolveParcel(ParcelLogic parcel, TileMap map, double oldX, double oldY) {
    if (map == null) return;

    double hw = MatchRules.PARCEL_SIZE / 2.0;
    double hh = MatchRules.PARCEL_SIZE / 2.0;

    double cx = parcel.x();
    double cy = parcel.y();
    double oldCy = oldY;

    int centerTileX = (int) Math.floor(cx / MatchRules.TILE_SIZE);
    int centerTileY = (int) Math.floor(cy / MatchRules.TILE_SIZE);

    // 1. Resolve X-axis using oldCy
    double leftEdge = cx - hw;
    int leftTileX = (int) Math.floor(leftEdge / MatchRules.TILE_SIZE);
    if (leftTileX < centerTileX) {
      if (isSolid(leftEdge, oldCy - hh, map) || isSolid(leftEdge, oldCy + hh, map)) {
        parcel.setX((leftTileX + 1) * MatchRules.TILE_SIZE + hw);
        parcel.bounceX();
      }
    }

    double rightEdge = cx + hw;
    int rightTileX = (int) Math.floor(rightEdge / MatchRules.TILE_SIZE);
    if (rightTileX > centerTileX) {
      if (isSolid(rightEdge, oldCy - hh, map) || isSolid(rightEdge, oldCy + hh, map)) {
        parcel.setX(rightTileX * MatchRules.TILE_SIZE - hw);
        parcel.bounceX();
      }
    }

    // Re-evaluate X center after potential snap
    cx = parcel.x();

    // 2. Resolve Y-axis using the new (potentially snapped) cx
    double topEdge = cy - hh;
    int topTileY = (int) Math.floor(topEdge / MatchRules.TILE_SIZE);
    if (topTileY < centerTileY) {
      if (isSolid(cx - hw, topEdge, map) || isSolid(cx + hw, topEdge, map)) {
        parcel.setY((topTileY + 1) * MatchRules.TILE_SIZE + hh);
        parcel.bounceY();
      }
    }

    double bottomEdge = cy + hh;
    int bottomTileY = (int) Math.floor(bottomEdge / MatchRules.TILE_SIZE);
    if (bottomTileY > centerTileY) {
      if (isSolid(cx - hw, bottomEdge, map) || isSolid(cx + hw, bottomEdge, map)) {
        parcel.setY(bottomTileY * MatchRules.TILE_SIZE - hh);
        parcel.bounceY();
      }
    }
  }

  public static void resolve(VehicleLogic vehicle, TileMap map, double oldX, double oldY) {
    if (map == null) return;

    double x = vehicle.x();
    double y = vehicle.y();

    double hw, hh, ox, oy;
    double rotation = vehicle.state().rotation();

    // North (0) or South (180)
    if (rotation == 0 || rotation == 180) {
      hw = MatchRules.VEHICLE_V_WIDTH / 2.0;
      hh = MatchRules.VEHICLE_V_HEIGHT / 2.0;
      ox = MatchRules.VEHICLE_V_OFFSET_X;
      oy = MatchRules.VEHICLE_V_OFFSET_Y;
    } else { // East (90) or West (270)
      hw = MatchRules.VEHICLE_H_WIDTH / 2.0;
      hh = MatchRules.VEHICLE_H_HEIGHT / 2.0;
      ox = MatchRules.VEHICLE_H_OFFSET_X;
      oy = MatchRules.VEHICLE_H_OFFSET_Y;
    }

    double cx = x + ox;
    double cy = y + oy;
    double oldCy = oldY + oy;

    int centerTileX = (int) Math.floor(cx / MatchRules.TILE_SIZE);
    int centerTileY = (int) Math.floor(cy / MatchRules.TILE_SIZE);

    // 1. Resolve X-axis using oldCy
    double leftEdge = cx - hw;
    int leftTileX = (int) Math.floor(leftEdge / MatchRules.TILE_SIZE);
    if (leftTileX < centerTileX) {
      if (isSolid(leftEdge, oldCy - hh + 2, map) || isSolid(leftEdge, oldCy + hh - 2, map)) {
        vehicle.setX((leftTileX + 1) * MatchRules.TILE_SIZE + hw - ox);
        vehicle.stopVelocityX();
      }
    }

    double rightEdge = cx + hw;
    int rightTileX = (int) Math.floor(rightEdge / MatchRules.TILE_SIZE);
    if (rightTileX > centerTileX) {
      if (isSolid(rightEdge, oldCy - hh + 2, map) || isSolid(rightEdge, oldCy + hh - 2, map)) {
        vehicle.setX(rightTileX * MatchRules.TILE_SIZE - hw - ox);
        vehicle.stopVelocityX();
      }
    }

    // Re-evaluate X center after potential snap
    x = vehicle.x();
    cx = x + ox;

    // 2. Resolve Y-axis using the new (potentially snapped) cx
    double topEdge = cy - hh;
    int topTileY = (int) Math.floor(topEdge / MatchRules.TILE_SIZE);
    if (topTileY < centerTileY) {
      if (isSolid(cx - hw + 2, topEdge, map) || isSolid(cx + hw - 2, topEdge, map)) {
        vehicle.setY((topTileY + 1) * MatchRules.TILE_SIZE + hh - oy);
        vehicle.stopVelocityY();
      }
    }

    double bottomEdge = cy + hh;
    int bottomTileY = (int) Math.floor(bottomEdge / MatchRules.TILE_SIZE);
    if (bottomTileY > centerTileY) {
      if (isSolid(cx - hw + 2, bottomEdge, map) || isSolid(cx + hw - 2, bottomEdge, map)) {
        vehicle.setY(bottomTileY * MatchRules.TILE_SIZE - hh - oy);
        vehicle.stopVelocityY();
      }
    }
  }

  private static boolean isSolid(double px, double py, TileMap map) {
    int tx = (int) Math.floor(px / MatchRules.TILE_SIZE);
    int ty = (int) Math.floor(py / MatchRules.TILE_SIZE);

    if (tx < 0 || tx >= map.getWidth() || ty < 0 || ty >= map.getHeight()) {
      return true;
    }

    return map.getTile(tx, ty).isSolid;
  }
}
