package com.battlecity.game;

public final class Tank {
    public final int entityId;
    public final int playerId;

    public float x;
    public float y;
    public float prevX;
    public float prevY;

    public final float spawnX;
    public final float spawnY;
    public int respawnCooldownTicks = -1;
    public boolean eliminated = false;

    public Direction dir = Direction.UP;
    public float speed = 120f;
    public float halfW = 6f;
    public float halfH = 6f;
    public boolean alive = true;
    public int fireCooldownTicks = 0;

    public Tank(int entityId, int playerId, float x, float y) {
        this.entityId = entityId;
        this.playerId = playerId;
        this.x = x;
        this.y = y;
        this.prevX = x;
        this.prevY = y;
        this.spawnX = x;
        this.spawnY = y;
    }
}
