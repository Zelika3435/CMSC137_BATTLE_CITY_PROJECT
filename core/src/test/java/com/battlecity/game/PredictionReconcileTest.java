package com.battlecity.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.battlecity.game.snapshot.TankSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Headless tests for {@link LocalPrediction} reconcile logic.
 *
 * <p>No LibGDX, no OpenGL, no network I/O.  All tests drive the prediction directly.
 *
 * <h3>What is tested</h3>
 * <ul>
 *   <li>Idle reconcile (no drift) — no snap, error ≈ 0.
 *   <li>Small drift — smooth correction applied; no snap flagged.
 *   <li>Large drift (&gt; {@link LocalPrediction#SNAP_THRESHOLD_PX}) — hard snap to server truth.
 *   <li>Dead tank from server — prediction snaps, alive flag corrected.
 *   <li>Direction is always corrected to server value regardless of error magnitude.
 *   <li>Several input steps followed by a matching server reconcile keeps error near zero.
 * </ul>
 */
final class PredictionReconcileTest {

    private static final float DT = 1f / 60f;
    private static final int ENTITY_ID = 1;
    private static final int PLAYER_ID = 0;

    /** 15×15 all-EMPTY map at tile-size 16 to give plenty of room. */
    private TileMap map;

    @BeforeEach
    void setUp() {
        map = new TileMap(15, 15, 16f);
    }

    // ---- helper constructors ---------------------------------------------------------------

    private static TankSnapshot snapshot(float x, float y, Direction dir, boolean alive) {
        return new TankSnapshot(ENTITY_ID, PLAYER_ID, x, y, x, y, dir, alive);
    }

    private static LocalPrediction newPrediction(TileMap map,
                                                  float x, float y, Direction dir) {
        return new LocalPrediction(snapshot(x, y, dir, true), map);
    }

    // ---- tests -----------------------------------------------------------------------------

    @Test
    void idleReconcile_noMovement_errorIsZero() {
        float startX = 100f, startY = 100f;
        LocalPrediction pred = newPrediction(map, startX, startY, Direction.UP);

        // Reconcile immediately without stepping — server agrees with our initial seed.
        float err = pred.reconcile(snapshot(startX, startY, Direction.UP, true));

        assertEquals(0f, err, 0.001f);
        assertFalse(pred.lastReconcileWasSnap());
    }

    @Test
    void smallDrift_smoothCorrection_noSnap() {
        float startX = 100f, startY = 100f;
        LocalPrediction pred = newPrediction(map, startX, startY, Direction.UP);

        // Simulate a tiny drift: prediction moved slightly, server has the original position.
        pred.applyInput(Direction.RIGHT);  // moves by speed * dt ≈ 2 units
        TankSnapshot server = snapshot(startX, startY, Direction.RIGHT, true);

        float err = pred.reconcile(server);

        // Error should be small (≈ 2 units) and less than the snap threshold.
        assertTrue(err < LocalPrediction.SNAP_THRESHOLD_PX,
                "expected small error but got " + err);
        assertFalse(pred.lastReconcileWasSnap(), "should smooth-correct, not snap");

        // After correction the predicted position should be closer to the server position.
        TankSnapshot corrected = pred.snapshot();
        float residual = Math.abs(corrected.x() - startX);
        assertTrue(residual < err, "smooth correction should reduce residual error");
    }

    @Test
    void largeDrift_hardSnap() {
        LocalPrediction pred = newPrediction(map, 100f, 100f, Direction.UP);

        // Server says the tank is far away — beyond the snap threshold.
        float serverX = 100f + LocalPrediction.SNAP_THRESHOLD_PX + 10f;
        TankSnapshot server = snapshot(serverX, 100f, Direction.UP, true);

        float err = pred.reconcile(server);

        assertTrue(err > LocalPrediction.SNAP_THRESHOLD_PX);
        assertTrue(pred.lastReconcileWasSnap(), "large error must trigger a snap");

        TankSnapshot after = pred.snapshot();
        assertEquals(serverX, after.x(), 0.001f, "position must snap to server value");
        assertEquals(100f, after.y(), 0.001f);
    }

    @Test
    void deadTank_snapsToServerAndClearsAlive() {
        LocalPrediction pred = newPrediction(map, 100f, 100f, Direction.UP);

        // Server says the tank died at a different position.
        TankSnapshot server = new TankSnapshot(ENTITY_ID, PLAYER_ID,
                80f, 90f, 80f, 90f, Direction.DOWN, false);

        pred.reconcile(server);

        assertTrue(pred.lastReconcileWasSnap(), "dead tank should snap");
        TankSnapshot after = pred.snapshot();
        assertFalse(after.alive(), "alive flag must be corrected");
        assertEquals(80f, after.x(), 0.001f);
        assertEquals(90f, after.y(), 0.001f);
    }

    @Test
    void directionAlwaysCorrectedToServer() {
        LocalPrediction pred = newPrediction(map, 100f, 100f, Direction.UP);

        // No positional drift — only direction differs.
        TankSnapshot server = snapshot(100f, 100f, Direction.LEFT, true);
        pred.reconcile(server);

        assertEquals(Direction.LEFT, pred.snapshot().dir(),
                "direction must always be corrected to server value");
        assertFalse(pred.lastReconcileWasSnap());
    }

    @Test
    void multipleStepsThenReconcile_matchingServer_staysBelowSnapThreshold() {
        // Start both prediction and a reference tank at the same position.
        float startX = 120f, startY = 120f;
        LocalPrediction pred = newPrediction(map, startX, startY, Direction.RIGHT);

        // Step the prediction 10 ticks to the right — mirror what the server also does.
        Tank refTank = new Tank(ENTITY_ID, PLAYER_ID, startX, startY);
        for (int i = 0; i < 10; i++) {
            pred.applyInput(Direction.RIGHT);
            MovementSystem.moveTank(refTank, Direction.RIGHT, DT, map);
        }

        // Server sends a snapshot reflecting the same movement.
        TankSnapshot server = snapshot(refTank.x, refTank.y, Direction.RIGHT, true);
        float err = pred.reconcile(server);

        assertTrue(err < LocalPrediction.SNAP_THRESHOLD_PX,
                "error after matching input sequence should stay well below snap threshold; got " + err);
    }
}
