package com.battlecity.core;

/**
 * All top-level app phases.
 *
 * <p>Only {@link #SINGLE_PLAYER}, {@link #TUTORIAL}, and {@link #MP_MATCH} run the deterministic
 * 60 Hz fixed-timestep simulation tick loop. All other phases use variable deltaTime (menus,
 * connect/lobby overlays, post-match screen).
 */
public enum AppPhase {
    MAIN_MENU,
    /** Pre-start panel for single-player: choose fixed or random bot seed before launching. */
    SP_PRESTART,
    SINGLE_PLAYER,
    TUTORIAL,
    MP_CONNECT,
    MP_LOBBY,
    MP_MATCH,
    MATCH_END;

    /** Returns {@code true} if this phase drives a fixed-timestep simulation tick loop. */
    public boolean isSimulationPhase() {
        return this == SINGLE_PLAYER || this == TUTORIAL || this == MP_MATCH;
    }
}
