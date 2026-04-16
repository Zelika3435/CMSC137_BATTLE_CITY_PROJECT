package com.battlecity.core;

final class Tank {
  float x;
  float y;

  float prevX;
  float prevY;

  Direction dir = Direction.UP;

  /**
   * Movement speed in world units (pixels) per second.
   */
  float speed = 120f;

  /**
   * Axis-aligned bounds (half-extents) used for clamping/collision.
   */
  float halfW = 6f;
  float halfH = 6f;

  Tank(float x, float y) {
    this.x = x;
    this.y = y;
    this.prevX = x;
    this.prevY = y;
  }
}

