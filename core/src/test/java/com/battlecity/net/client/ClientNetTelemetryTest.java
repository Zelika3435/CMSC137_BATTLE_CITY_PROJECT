package com.battlecity.net.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ClientNetTelemetryTest {

    @Test
    void snapshotAgeTicksGrowsWithElapsedTime() {
        ClientNetTelemetry telemetry = new ClientNetTelemetry();
        telemetry.onSnapshotReceived(100L, 1000L);
        assertEquals(0, telemetry.snapshotAgeTicks(1000L));
        assertEquals(3, telemetry.snapshotAgeTicks(1050L));
    }

    @Test
    void snapshotsPerSecondUsesRollingWindow() {
        ClientNetTelemetry telemetry = new ClientNetTelemetry();
        for (int i = 0; i < 60; i++) {
            telemetry.onSnapshotReceived(i, 1000L + i * 16L);
        }
        telemetry.refresh(2000L);
        assertEquals(60f, telemetry.snapshotsPerSecond(), 0.01f);
    }

    @Test
    void unackedInputsTracksSentMinusAcked() {
        ClientNetTelemetry telemetry = new ClientNetTelemetry();
        telemetry.onInputSent(5);
        telemetry.onInputAck(2);
        assertEquals(3, telemetry.unackedInputs());
        telemetry.onInputAck(5);
        assertEquals(0, telemetry.unackedInputs());
    }
}
