package com.battlecity.net.client;

/**
 * Client-side network health counters for the F3 overlay. Updated on the LibGDX thread when
 * snapshots arrive, inputs are sent, or {@link #refresh(long)} runs; read-only for rendering.
 */
final class ClientNetTelemetry {

    private static final long RATE_WINDOW_MS = 1000L;

    private long rateWindowStartMs = -1L;
    private int rateWindowCount;
    private float snapshotsPerSecond;

    private long lastSnapshotReceiveMs = -1L;
    private long lastSnapshotServerTick = -1L;

    private int lastInputSeqSent;
    private int lastInputSeqAcked;

    void onSnapshotReceived(long serverTick, long nowMs) {
        rateWindowCount++;
        lastSnapshotReceiveMs = nowMs;
        lastSnapshotServerTick = serverTick;
        rollRateWindow(nowMs);
    }

    void onInputSent(int seq) {
        if (seq > lastInputSeqSent) {
            lastInputSeqSent = seq;
        }
    }

    void onInputAck(int ack) {
        if (ack > lastInputSeqAcked) {
            lastInputSeqAcked = ack;
        }
    }

    void refresh(long nowMs) {
        rollRateWindow(nowMs);
    }

    float snapshotsPerSecond() {
        return snapshotsPerSecond;
    }

    int snapshotAgeTicks(long nowMs) {
        if (lastSnapshotServerTick < 0L || lastSnapshotReceiveMs < 0L) {
            return 0;
        }
        long elapsedMs = Math.max(0L, nowMs - lastSnapshotReceiveMs);
        return (int) ((elapsedMs * 60L) / 1000L);
    }

    int unackedInputs() {
        return Math.max(0, lastInputSeqSent - lastInputSeqAcked);
    }

    void reset() {
        rateWindowStartMs = -1L;
        rateWindowCount = 0;
        snapshotsPerSecond = 0f;
        lastSnapshotReceiveMs = -1L;
        lastSnapshotServerTick = -1L;
        lastInputSeqSent = 0;
        lastInputSeqAcked = 0;
    }

    private void rollRateWindow(long nowMs) {
        if (rateWindowStartMs < 0L) {
            rateWindowStartMs = nowMs;
            return;
        }
        long elapsed = nowMs - rateWindowStartMs;
        if (elapsed < RATE_WINDOW_MS) {
            return;
        }
        snapshotsPerSecond = rateWindowCount * 1000f / elapsed;
        rateWindowStartMs = nowMs;
        rateWindowCount = 0;
    }
}
