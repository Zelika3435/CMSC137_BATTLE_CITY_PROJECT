package com.battlecity.core;

final class Projectile {
  boolean active = false;

  float x;
  float y;
  float prevX;
  float prevY;

  Direction dir = Direction.UP;
  float speed = 240f;

  float halfW = 2f;
  float halfH = 2f;

  void spawn(float x, float y, Direction dir) {
    this.active = true;
    this.x = x;
    this.y = y;
    this.prevX = x;
    this.prevY = y;
    this.dir = dir;
  }

  void despawn() {
    this.active = false;
  }
}

