package com.battlecity.game;

import java.util.Random;

public final class SeededRng implements Rng {
    private final Random random;

    public SeededRng(long seed) {
        this.random = new Random(seed);
    }

    @Override
    public int nextInt(int bound) {
        return random.nextInt(bound);
    }

    @Override
    public int nextInt() {
        return random.nextInt();
    }
}
