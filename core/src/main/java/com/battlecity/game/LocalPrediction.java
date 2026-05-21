package com.battlecity.game;

import com.battlecity.game.snapshot.TankSnapshot;

/**
 * Lightweight client-side prediction for the local player's tank in {@code MP_MATCH}.
 *
 * <p>Advances the predicted tank position one fixed-timestep tick at a time using the same
 * {@link MovementSystem} rules as the server.  On each received server snapshot the predicted
 * state is <em>reconciled</em>:
 * <ul>
 *   <li>If the positional error is larger than {@link #SNAP_THRESHOLD_PX} the pose is
 *       hard-snapped to server truth.
 *   <li>Otherwise a fraction ({@link #SMOOTH_FACTOR}) of the error is blended away
 *       each reconcile, yielding a gentle drift correction that is invisible to the player.
 *   <li>Direction and alive state are always corrected to the server-authoritative value.
 * </ul>
 *
 * <p>This class does <em>not</em> affect the server simulation in any way; it is pure
 * presentational state owned by the client.  Must be mutated exclusively from the LibGDX
 * render thread (same thread as {@link com.battlecity.net.client.GameClient}).
 */
public final class LocalPrediction {

    /** World-unit error above which the predicted pose is hard-snapped to server truth. */
    public static final float SNAP_THRESHOLD_PX = 16f;

    /**
     * Fraction of positional error corrected per reconcile when below the snap threshold.
     * A value of 0.25 means 25 % of the gap is closed each snapshot arrival (≈ 20 Hz).
     */
    public static final float SMOOTH_FACTOR = 0.25f;

    private static final float FIXED_DT = 1f / 60f;

    private final Tank tank;
    private TileMap map;

    /** Positional error (world units) observed during the most recent {@link #reconcile}. */
    private float lastErrorPx;

    /** {@code true} if the most recent reconcile resulted in a hard snap. */
    private boolean lastReconcileWasSnap;

    /**
     * Constructs a prediction seeded from a server-authoritative tank snapshot.
     *
     * @param initial    the initial server tank state for the local player
     * @param initialMap tile map used for collision queries; should be kept current via
     *                   {@link #updateMap(TileMap)}
     */
    public LocalPrediction(TankSnapshot initial, TileMap initialMap) {
        this.map = initialMap;
        this.tank = new Tank(initial.entityId(), initial.playerId(), initial.x(), initial.y());
        this.tank.dir   = initial.dir();
        this.tank.alive = initial.alive();
        this.tank.prevX = initial.prevX();
        this.tank.prevY = initial.prevY();
    }

    // ---- Per-tick input application --------------------------------------------------------

    /**
     * Advances the predicted tank state by one fixed timestep using the supplied movement
     * direction.  Call once per client tick, ideally just after sending the same input to the
     * server, so the local rendering stays ahead by approximately RTT/2.
     *
     * <p>No-op when the predicted tank is not alive.
     *
     * @param moveDir movement direction this tick, or {@code null} for no movement
     */
    public void applyInput(Direction moveDir) {
        if (!tank.alive) {
            return;
        }
        tank.prevX = tank.x;
        tank.prevY = tank.y;
        if (moveDir != null) {
            MovementSystem.moveTank(tank, moveDir, FIXED_DT, map);
        }
    }

    // ---- Map update ------------------------------------------------------------------------

    /**
     * Refreshes the tile map used for collision during prediction.
     * Call whenever a new server snapshot is applied so tile destructions are reflected.
     */
    public void updateMap(TileMap newMap) {
        this.map = newMap;
    }

    // ---- Reconciliation --------------------------------------------------------------------

    /**
     * Reconciles the predicted state against an authoritative server tank snapshot.
     *
     * <p>Correction rules (in priority order):
     * <ol>
     *   <li>Direction: always set to server value.
     *   <li>Alive: always set to server value; dead tanks are hard-snapped to server position.
     *   <li>Position: snapped if {@code error > }{@link #SNAP_THRESHOLD_PX}; otherwise
     *       smoothly blended by {@link #SMOOTH_FACTOR}.
     * </ol>
     *
     * @param serverTank the server-authoritative tank entry for the local player
     * @return the positional error in world units measured <em>before</em> correction;
     *         useful for the debug overlay
     */
    public float reconcile(TankSnapshot serverTank) {
        float errX = serverTank.x() - tank.x;
        float errY = serverTank.y() - tank.y;
        float err  = (float) Math.sqrt(errX * errX + errY * errY);
        lastErrorPx = err;

        // Direction and alive are always server-authoritative.
        tank.dir   = serverTank.dir();
        tank.alive = serverTank.alive();

        if (!serverTank.alive() || err > SNAP_THRESHOLD_PX) {
            // Hard snap: dead or too far off.
            tank.x     = serverTank.x();
            tank.y     = serverTank.y();
            tank.prevX = serverTank.prevX();
            tank.prevY = serverTank.prevY();
            lastReconcileWasSnap = true;
        } else if (err > 0.001f) {
            // Smooth correction: gently nudge toward server truth.
            tank.x += errX * SMOOTH_FACTOR;
            tank.y += errY * SMOOTH_FACTOR;
            // Wipe prevX/Y so sub-tick interpolation doesn't stutter.
            tank.prevX = tank.x;
            tank.prevY = tank.y;
            lastReconcileWasSnap = false;
        } else {
            lastReconcileWasSnap = false;
        }
        return err;
    }

    // ---- Snapshot / read accessors ---------------------------------------------------------

    /**
     * Returns an immutable snapshot of the current predicted pose suitable for rendering.
     * The returned record carries the predicted {@code prevX/Y} so the caller can interpolate
     * between ticks using {@code MathUtils.lerp(snapshot.prevX(), snapshot.x(), alpha)}.
     */
    public TankSnapshot snapshot() {
        return new TankSnapshot(
                tank.entityId,
                tank.playerId,
                tank.x,
                tank.y,
                tank.prevX,
                tank.prevY,
                tank.dir,
                tank.alive
        );
    }

    /** Positional error (world units) recorded during the most recent {@link #reconcile}. */
    public float lastErrorPx() {
        return lastErrorPx;
    }

    /**
     * {@code true} if the most recent {@link #reconcile} resulted in a hard snap to server
     * truth (error exceeded {@link #SNAP_THRESHOLD_PX} or the tank died).
     */
    public boolean lastReconcileWasSnap() {
        return lastReconcileWasSnap;
    }
}
