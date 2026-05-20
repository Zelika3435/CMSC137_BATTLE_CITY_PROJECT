package com.battlecity.game;

public final class Projectile {
    public final int entityId;

    public boolean active = false;
    public float x;
    public float y;
    public float prevX;
    public float prevY;
    public Direction dir = Direction.UP;
    public float speed = 240f;
    public float halfW = 2f;
    public float halfH = 2f;
    public int ownerPlayerId = -1;

    public Projectile(int entityId) {
        this.entityId = entityId;
    }

    public void spawn(float x, float y, Direction dir, int ownerPlayerId) {
        this.active = true;
        this.x = x;
        this.y = y;
        this.prevX = x;
        this.prevY = y;
        this.dir = dir;
        this.ownerPlayerId = ownerPlayerId;
    }

    public void despawn() {
        this.active = false;
    }
}
