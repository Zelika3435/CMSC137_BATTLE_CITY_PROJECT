package com.battlecity.game.event;

public sealed interface GameEvent permits
        ProjectileHitTank,
        TankDestroyed,
        TileDestroyed,
        BaseHit {
}
