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

  /** Whether this tank is still alive. */
  boolean alive = true;

  /** True for the human-controlled player, false for bot enemies. */
  boolean isPlayer;

  /** Ticks remaining before this tank can fire again (used by bot AI). */
  int fireCooldownTicks = 0;

  Tank(float x, float y, boolean isPlayer) {
    this.x = x;
    this.y = y;
    this.prevX = x;
    this.prevY = y;
    this.isPlayer = isPlayer;
  }
}

