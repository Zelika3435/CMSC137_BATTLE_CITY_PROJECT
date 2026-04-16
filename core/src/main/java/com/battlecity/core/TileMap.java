package com.battlecity.core;

import java.util.Arrays;

final class TileMap {
  private final int widthTiles;
  private final int heightTiles;
  private final float tileSize;
  private final Tile[] tiles;

  TileMap(int widthTiles, int heightTiles, float tileSize) {
    if (widthTiles <= 0 || heightTiles <= 0) throw new IllegalArgumentException("invalid map size");
    if (tileSize <= 0f) throw new IllegalArgumentException("invalid tileSize");
    this.widthTiles = widthTiles;
    this.heightTiles = heightTiles;
    this.tileSize = tileSize;
    this.tiles = new Tile[widthTiles * heightTiles];
    Arrays.fill(this.tiles, Tile.EMPTY);
  }

  int widthTiles() {
    return widthTiles;
  }

  int heightTiles() {
    return heightTiles;
  }

  float tileSize() {
    return tileSize;
  }

  float worldW() {
    return widthTiles * tileSize;
  }

  float worldH() {
    return heightTiles * tileSize;
  }

  Tile tileAt(int tx, int ty) {
    if (!inBounds(tx, ty)) return Tile.STEEL; // treat outside map as solid boundary
    return tiles[ty * widthTiles + tx];
  }

  void setTile(int tx, int ty, Tile tile) {
    if (!inBounds(tx, ty)) throw new IndexOutOfBoundsException("tile out of bounds");
    tiles[ty * widthTiles + tx] = tile;
  }

  boolean isSolid(int tx, int ty) {
    Tile t = tileAt(tx, ty);
    return t == Tile.BRICK || t == Tile.STEEL || t == Tile.BASE;
  }

  boolean inBounds(int tx, int ty) {
    return tx >= 0 && tx < widthTiles && ty >= 0 && ty < heightTiles;
  }
}

