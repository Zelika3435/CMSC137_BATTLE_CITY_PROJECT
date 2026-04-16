package com.battlecity.core;

final class CollisionSystem {
  private static final float EPS = 0.0001f;

  private CollisionSystem() {}

  static void resolveTankVsMap(Tank tank, TileMap map) {
    float dx = tank.x - tank.prevX;
    float dy = tank.y - tank.prevY;

    if (dx != 0f) {
      resolveHorizontal(tank, map, dx);
    }
    if (dy != 0f) {
      resolveVertical(tank, map, dy);
    }
  }

  private static void resolveHorizontal(Tank tank, TileMap map, float dx) {
    float tile = map.tileSize();
    float minY = tank.y - tank.halfH;
    float maxY = tank.y + tank.halfH;
    int ty0 = (int) Math.floor((minY + EPS) / tile);
    int ty1 = (int) Math.floor((maxY - EPS) / tile);

    if (dx > 0f) {
      float rightEdge = tank.x + tank.halfW;
      int tx = (int) Math.floor((rightEdge - EPS) / tile);
      for (int ty = ty0; ty <= ty1; ty++) {
        if (map.isSolid(tx, ty)) {
          float tileLeft = tx * tile;
          tank.x = tileLeft - tank.halfW;
          return;
        }
      }
    } else {
      float leftEdge = tank.x - tank.halfW;
      int tx = (int) Math.floor((leftEdge + EPS) / tile);
      for (int ty = ty0; ty <= ty1; ty++) {
        if (map.isSolid(tx, ty)) {
          float tileRight = (tx + 1) * tile;
          tank.x = tileRight + tank.halfW;
          return;
        }
      }
    }
  }

  private static void resolveVertical(Tank tank, TileMap map, float dy) {
    float tile = map.tileSize();
    float minX = tank.x - tank.halfW;
    float maxX = tank.x + tank.halfW;
    int tx0 = (int) Math.floor((minX + EPS) / tile);
    int tx1 = (int) Math.floor((maxX - EPS) / tile);

    if (dy > 0f) {
      float topEdge = tank.y + tank.halfH;
      int ty = (int) Math.floor((topEdge - EPS) / tile);
      for (int tx = tx0; tx <= tx1; tx++) {
        if (map.isSolid(tx, ty)) {
          float tileBottom = ty * tile;
          tank.y = tileBottom - tank.halfH;
          return;
        }
      }
    } else {
      float bottomEdge = tank.y - tank.halfH;
      int ty = (int) Math.floor((bottomEdge + EPS) / tile);
      for (int tx = tx0; tx <= tx1; tx++) {
        if (map.isSolid(tx, ty)) {
          float tileTop = (ty + 1) * tile;
          tank.y = tileTop + tank.halfH;
          return;
        }
      }
    }
  }
}

