package com.battlecity.game;

public final class MathUtil {
    private MathUtil() {}

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float lerp(float from, float to, float alpha) {
        return from + (to - from) * alpha;
    }
}
