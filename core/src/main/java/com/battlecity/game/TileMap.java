package com.battlecity.game;

import java.util.Arrays;

public final class TileMap {
    private final int widthTiles;
    private final int heightTiles;
    private final float tileSize;
    private final Tile[] tiles;

    public TileMap(int widthTiles, int heightTiles, float tileSize) {
        if (widthTiles <= 0 || heightTiles <= 0) {
            throw new IllegalArgumentException("invalid map size");
        }
        if (tileSize <= 0f) {
            throw new IllegalArgumentException("invalid tileSize");
        }
        this.widthTiles = widthTiles;
        this.heightTiles = heightTiles;
        this.tileSize = tileSize;
        this.tiles = new Tile[widthTiles * heightTiles];
        Arrays.fill(this.tiles, Tile.EMPTY);
    }

    public int widthTiles() {
        return widthTiles;
    }

    public int heightTiles() {
        return heightTiles;
    }

    public float tileSize() {
        return tileSize;
    }

    public float worldW() {
        return widthTiles * tileSize;
    }

    public float worldH() {
        return heightTiles * tileSize;
    }

    public Tile tileAt(int tx, int ty) {
        if (!inBounds(tx, ty)) {
            return Tile.STEEL;
        }
        return tiles[ty * widthTiles + tx];
    }

    public void setTile(int tx, int ty, Tile tile) {
        if (!inBounds(tx, ty)) {
            throw new IndexOutOfBoundsException("tile out of bounds");
        }
        tiles[ty * widthTiles + tx] = tile;
    }

    public boolean isSolid(int tx, int ty) {
        Tile t = tileAt(tx, ty);
        return t == Tile.BRICK || t == Tile.STEEL || t == Tile.BASE;
    }

    public boolean inBounds(int tx, int ty) {
        return tx >= 0 && tx < widthTiles && ty >= 0 && ty < heightTiles;
    }

    public Tile[] copyTiles() {
        return Arrays.copyOf(tiles, tiles.length);
    }

    public void copyFrom(Tile[] source) {
        if (source.length != tiles.length) {
            throw new IllegalArgumentException("tile array size mismatch");
        }
        System.arraycopy(source, 0, tiles, 0, tiles.length);
    }
}
