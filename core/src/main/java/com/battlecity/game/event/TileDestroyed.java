package com.battlecity.game.event;

import com.battlecity.game.Tile;

public record TileDestroyed(int tileX, int tileY, Tile previousTile) implements GameEvent {}
