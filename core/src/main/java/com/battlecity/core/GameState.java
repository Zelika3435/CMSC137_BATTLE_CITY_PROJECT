package com.battlecity.core;

final class GameState {
  final Tank player = new Tank(64f, 64f, true);

  /** Enemy bot tanks (3 enemies, spawned at the top of the map). */
  final Tank[] enemies;

  final TileMap map = new TileMap(26, 26, 16f);

  final Projectile[] projectiles = new Projectile[64];

  GameState() {
    for (int i = 0; i < projectiles.length; i++) {
      projectiles[i] = new Projectile();
    }

    // Simple demo layout so the renderer shows walls immediately.
    // Borders: STEEL
    for (int x = 0; x < map.widthTiles(); x++) {
      map.setTile(x, 0, Tile.STEEL);
      map.setTile(x, map.heightTiles() - 1, Tile.STEEL);
    }
    for (int y = 0; y < map.heightTiles(); y++) {
      map.setTile(0, y, Tile.STEEL);
      map.setTile(map.widthTiles() - 1, y, Tile.STEEL);
    }

    // A few BRICK clusters.
    for (int y = 6; y <= 8; y++) {
      for (int x = 6; x <= 9; x++) {
        map.setTile(x, y, Tile.BRICK);
      }
    }
    for (int y = 14; y <= 16; y++) {
      for (int x = 15; x <= 18; x++) {
        map.setTile(x, y, Tile.BRICK);
      }
    }

    // A small STEEL block.
    map.setTile(12, 12, Tile.STEEL);
    map.setTile(13, 12, Tile.STEEL);
    map.setTile(12, 13, Tile.STEEL);
    map.setTile(13, 13, Tile.STEEL);

    // Spawn 3 enemy tanks near the top of the map (classic Battle City positions).
    float mapW = map.worldW();
    float topY = map.worldH() - 32f; // a couple tiles below the top border
    enemies = new Tank[] {
      new Tank(mapW * 0.25f, topY, false),  // top-left area
      new Tank(mapW * 0.50f, topY, false),  // top-center
      new Tank(mapW * 0.75f, topY, false),  // top-right area
    };
    // Give enemies slightly slower speed so the player has an advantage.
    for (Tank e : enemies) {
      e.speed = 80f;
      e.dir = Direction.DOWN;
    }
  }

  long tickCount = 0L;

  int fireCount = 0;

  boolean showDebug = true;
  boolean prevF3Down = false;
  boolean prevSpaceDown = false;
}
