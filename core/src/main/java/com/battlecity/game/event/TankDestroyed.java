package com.battlecity.game.event;

public record TankDestroyed(int tankEntityId, int playerId) implements GameEvent {}
