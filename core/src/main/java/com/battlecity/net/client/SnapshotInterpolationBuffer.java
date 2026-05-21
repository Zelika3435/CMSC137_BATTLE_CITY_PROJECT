package com.battlecity.net.client;

import com.battlecity.game.snapshot.GameSnapshot;
import java.util.ArrayList;
import java.util.List;

/**
 * Queues authoritative snapshots by {@code serverTick} and samples a display-time pose
 * {@code estimatedServerTick - bufferTicks} for render interpolation.
 *
 * <p>LibGDX render thread only. Out-of-order packets replace the same tick; missing ticks
 * hold the last bracketing snapshot (no throw).
 */
public final class SnapshotInterpolationBuffer {

    /** Default display delay for LAN play (50–100 ms). */
    public static final int DEFAULT_BUFFER_MS = 75;

    private static final float FIXED_DT_SEC = GameClient.SimulationConstants.FIXED_DT;
    private static final int MAX_STORED = 64;
    /** Minimum snapshots retained after pruning. */
    private static final int MIN_RETAINED = 3;

    private final int bufferMs;
    private final List<Long> ticks = new ArrayList<>();
    private final List<GameSnapshot> snapshots = new ArrayList<>();

    private GameSnapshot latestSnapshot;
    private long latestTick = -1L;
    private long lastSnapReceiveMs;

    public SnapshotInterpolationBuffer() {
        this(DEFAULT_BUFFER_MS);
    }

    public SnapshotInterpolationBuffer(int bufferMs) {
        if (bufferMs < 0) {
            throw new IllegalArgumentException("bufferMs must be non-negative");
        }
        this.bufferMs = bufferMs;
    }

    public int bufferDelayMs() {
        return bufferMs;
    }

    public GameSnapshot latestSnapshot() {
        return latestSnapshot;
    }

    public void clear() {
        ticks.clear();
        snapshots.clear();
        latestSnapshot = null;
        latestTick = -1L;
        lastSnapReceiveMs = 0L;
    }

    public void push(GameSnapshot snapshot) {
        long tick = snapshot.serverTick();
        latestSnapshot = snapshot;
        latestTick = tick;
        lastSnapReceiveMs = System.currentTimeMillis();

        int idx = binarySearchTick(tick);
        if (idx >= 0) {
            snapshots.set(idx, snapshot);
            return;
        }
        int insertAt = -(idx + 1);
        ticks.add(insertAt, tick);
        snapshots.add(insertAt, snapshot);
        prune();
    }

    /**
     * Picks buffered prev/cur for the delayed display clock and fractional alpha in {@code [0, 1]}.
     *
     * @return {@code null} when the queue is empty
     */
    public InterpolationSample sample() {
        if (snapshots.isEmpty()) {
            return null;
        }
        float displayTick = displayServerTick();
        int last = snapshots.size() - 1;

        if (displayTick <= ticks.get(0)) {
            GameSnapshot snap = snapshots.get(0);
            return new InterpolationSample(snap, snap, 0f);
        }
        if (displayTick >= ticks.get(last)) {
            GameSnapshot snap = snapshots.get(last);
            return new InterpolationSample(snap, snap, 0f);
        }

        int prevIdx = -1;
        int curIdx = -1;
        for (int i = 0; i < ticks.size(); i++) {
            long t = ticks.get(i);
            if (t <= displayTick) {
                prevIdx = i;
            } else if (curIdx < 0) {
                curIdx = i;
            }
        }

        if (prevIdx < 0) {
            GameSnapshot snap = snapshots.get(0);
            return new InterpolationSample(snap, snap, 0f);
        }
        if (curIdx < 0) {
            GameSnapshot snap = snapshots.get(prevIdx);
            return new InterpolationSample(snap, snap, 0f);
        }

        long t0 = ticks.get(prevIdx);
        long t1 = ticks.get(curIdx);
        float alpha = t1 == t0 ? 0f : (displayTick - t0) / (t1 - t0);
        alpha = Math.max(0f, Math.min(1f, alpha));
        return new InterpolationSample(snapshots.get(prevIdx), snapshots.get(curIdx), alpha);
    }

    private float displayServerTick() {
        if (latestTick < 0L) {
            return 0f;
        }
        long elapsedMs = Math.max(0L, System.currentTimeMillis() - lastSnapReceiveMs);
        float estimated = latestTick + elapsedMs / 1000f / FIXED_DT_SEC;
        float bufferTicks = bufferMs / 1000f / FIXED_DT_SEC;
        return estimated - bufferTicks;
    }

    private int binarySearchTick(long tick) {
        int lo = 0;
        int hi = ticks.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            long midTick = ticks.get(mid);
            if (midTick < tick) {
                lo = mid + 1;
            } else if (midTick > tick) {
                hi = mid - 1;
            } else {
                return mid;
            }
        }
        return -(lo + 1);
    }

    private void prune() {
        while (snapshots.size() > MAX_STORED) {
            ticks.remove(0);
            snapshots.remove(0);
        }
        float displayTick = displayServerTick();
        while (snapshots.size() > MIN_RETAINED
                && ticks.get(0) < displayTick - MIN_RETAINED) {
            ticks.remove(0);
            snapshots.remove(0);
        }
    }

    public record InterpolationSample(GameSnapshot prev, GameSnapshot cur, float alpha) {}
}
