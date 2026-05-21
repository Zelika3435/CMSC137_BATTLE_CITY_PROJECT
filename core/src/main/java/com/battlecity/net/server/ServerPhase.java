package com.battlecity.net.server;

/**
 * Lifecycle phases of the authoritative {@link GameServer}.
 *
 * <pre>
 *  LOBBY → COUNTDOWN → RUNNING → END
 * </pre>
 *
 * <ul>
 *   <li>{@link #LOBBY}     — server is open; clients may join; no simulation world exists yet.
 *   <li>{@link #COUNTDOWN} — quorum reached; brief delay before the match starts; still no
 *                            simulation world (no tank/projectile state).
 *   <li>{@link #RUNNING}   — authoritative simulation is active; snapshots broadcast every tick.
 *   <li>{@link #END}       — match is over; simulation frozen; no more snapshots.
 * </ul>
 */
public enum ServerPhase {
    LOBBY,
    COUNTDOWN,
    RUNNING,
    END
}
