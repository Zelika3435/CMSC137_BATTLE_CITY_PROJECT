package com.battlecity.core;

/**
 * Contract for per-phase drivers. All methods run on the LibGDX render thread.
 *
 * <p>Simulation phases ({@link AppPhase#isSimulationPhase()}) must maintain their own
 * fixed-timestep accumulator inside {@link #update(float)} and never skip ticks based on
 * wall-clock jitter.
 */
public interface PhaseHandler {

    /**
     * Called once per frame with the capped raw delta time in seconds.
     *
     * <p>Simulation phases run the 60 Hz tick loop here. Menu/connect phases use {@code dt}
     * directly for animations and UI logic.
     *
     * @return the next {@link AppPhase} to transition to, or the current phase if no change
     */
    AppPhase update(float dt);

    /**
     * Draw the current phase. Called with {@link SpriteBatch} already open (begin/end managed by
     * {@code CoreGame}). Must not mutate simulation state.
     */
    void render();

    /** Called before this driver is replaced so it can clean up phase-owned transient state. */
    void onExit();

    /** Release any resources allocated by this driver (not shared {@link PhaseContext} resources). */
    void dispose();
}
