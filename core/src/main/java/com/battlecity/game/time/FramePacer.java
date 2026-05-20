package com.battlecity.game.time;

/**
 * Fixed-timestep pacing without {@link Thread#sleep} in simulation loops.
 */
public final class FramePacer {
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final double tickRateHz;
    private final long tickNanos;
    private long lastNanos = System.nanoTime();
    private double accumulatorSeconds = 0.0;

    public FramePacer(double tickRateHz) {
        this.tickRateHz = tickRateHz;
        this.tickNanos = (long) (NANOS_PER_SECOND / tickRateHz);
    }

    public int consumeDueTicks() {
        long now = System.nanoTime();
        long elapsed = now - lastNanos;
        lastNanos = now;
        accumulatorSeconds += elapsed / (double) NANOS_PER_SECOND;

        int ticks = 0;
        double tickSeconds = 1.0 / tickRateHz;
        while (accumulatorSeconds >= tickSeconds) {
            accumulatorSeconds -= tickSeconds;
            ticks++;
        }
        if (ticks > 5) {
            ticks = 5;
            accumulatorSeconds = 0.0;
        }
        return ticks;
    }

    public float alpha() {
        double tickSeconds = 1.0 / tickRateHz;
        return (float) (accumulatorSeconds / tickSeconds);
    }

    public void spinWaitForNextTick() {
        long target = lastNanos + tickNanos;
        while (System.nanoTime() < target) {
            Thread.onSpinWait();
        }
    }
}
