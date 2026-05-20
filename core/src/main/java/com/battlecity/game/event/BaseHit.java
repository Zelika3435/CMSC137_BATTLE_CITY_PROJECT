package com.battlecity.game.event;

public record BaseHit(int tileX, int tileY, int hitByPlayerId) implements GameEvent {}
