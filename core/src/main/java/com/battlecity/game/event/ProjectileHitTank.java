package com.battlecity.game.event;

public record ProjectileHitTank(int projectileEntityId, int tankEntityId, int ownerPlayerId) implements GameEvent {}
