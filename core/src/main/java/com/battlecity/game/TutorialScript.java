package com.battlecity.game;

import com.battlecity.game.event.BaseHit;
import com.battlecity.game.event.GameEvent;
import com.battlecity.game.event.TileDestroyed;
import com.battlecity.game.snapshot.GameSnapshot;
import com.battlecity.game.snapshot.TankSnapshot;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic step-by-step tutorial state machine.
 *
 * <p>Steps advance purely from simulation conditions (events, tank position/direction) — never
 * from wall-clock time. This makes the script fully headless-testable.
 *
 * <p>Caller contract per tick:
 * <ol>
 *   <li>Call {@link #init(GameSnapshot)} <em>once</em> with the initial snapshot.
 *   <li>After each simulation tick, call {@link #evaluate(GameSnapshot, List)} with the latest
 *       snapshot and the tick's events (from {@code LocalMatchController.lastEvents()}).
 *   <li>Call {@link #drainUiEvents()} to consume {@link TutorialStepComplete} signals for the
 *       overlay renderer.  Returns an empty list when no step advanced.
 * </ol>
 *
 * <p>At most one step advances per {@link #evaluate} call, so steps are always visible to the
 * UI for at least one frame.
 */
public final class TutorialScript {

    // ---- Step enum --------------------------------------------------------------------------

    public enum Step {
        /** Step 1: Move the tank southward to learn movement. */
        MOVE_SOUTH("Move your tank south",         "Press S or the DOWN arrow"),
        /** Step 2: Face north in preparation for firing. */
        FACE_NORTH("Face north",                   "Press W or the UP arrow"),
        /** Step 3: Destroy the brick wall. */
        DESTROY_BRICK("Destroy the brick wall",    "Move up (W/↑) and press SPACE to fire"),
        /** Step 4: Shoot and destroy the BASE tiles. */
        DESTROY_BASE("Destroy the base",           "Drive north, fire at the BASE"),
        /** Terminal state — tutorial is complete. */
        DONE("Tutorial complete!",                 "Press ESC to return to the menu");

        private final String title;
        private final String hint;

        Step(String title, String hint) {
            this.title = title;
            this.hint  = hint;
        }

        public String title() { return title; }
        public String hint()  { return hint; }
    }

    // ---- Constants --------------------------------------------------------------------------

    /** Number of active steps (excludes {@link Step#DONE}). */
    public static final int TOTAL_STEPS = 4;

    /** Tank must move this far south from its spawn to complete MOVE_SOUTH. */
    private static final float MOVE_SOUTH_THRESHOLD = MapFactory.DEFAULT_TILE_SIZE; // 1 tile = 16 px

    // ---- State ------------------------------------------------------------------------------

    private Step  current = Step.MOVE_SOUTH;
    private float spawnX;
    private float spawnY;

    private final List<TutorialStepComplete> uiEvents = new ArrayList<>();

    // ---- API --------------------------------------------------------------------------------

    /**
     * Records the player's spawn position from the initial snapshot.
     * Must be called exactly once before the first {@link #evaluate} call.
     */
    public void init(GameSnapshot snapshot) {
        TankSnapshot player = playerTank(snapshot);
        if (player != null) {
            spawnX = player.x();
            spawnY = player.y();
        }
    }

    /**
     * Evaluates the latest simulation state and advances at most one step.
     *
     * <p>Deterministic: reads only {@code snapshot} and {@code events}; no wall-clock access.
     *
     * @param snapshot latest immutable snapshot produced after the tick
     * @param events   events from the same tick ({@code LocalMatchController.lastEvents()})
     */
    public void evaluate(GameSnapshot snapshot, List<GameEvent> events) {
        if (snapshot.matchOver() && current != Step.DONE) {
            advance();
            return;
        }

        TankSnapshot player = playerTank(snapshot);
        if (player == null || !player.alive()) {
            return;
        }

        switch (current) {
            case MOVE_SOUTH -> {
                if (player.y() < spawnY - MOVE_SOUTH_THRESHOLD) {
                    advance();
                }
            }
            case FACE_NORTH -> {
                if (player.dir() == Direction.UP) {
                    advance();
                }
            }
            case DESTROY_BRICK -> {
                for (GameEvent e : events) {
                    if (e instanceof TileDestroyed td && td.previousTile() == Tile.BRICK) {
                        advance();
                        break;
                    }
                }
            }
            case DESTROY_BASE -> {
                for (GameEvent e : events) {
                    if (e instanceof BaseHit) {
                        advance();
                        break;
                    }
                }
            }
            case DONE -> { /* terminal — nothing to do */ }
        }
    }

    /** Current active step. */
    public Step currentStep() { return current; }

    /**
     * 1-based display index for the current step ({@link #TOTAL_STEPS}+1 when done).
     * Use this to render "Step N / {@link #TOTAL_STEPS}".
     */
    public int currentStepNumber() {
        return switch (current) {
            case MOVE_SOUTH    -> 1;
            case FACE_NORTH    -> 2;
            case DESTROY_BRICK -> 3;
            case DESTROY_BASE  -> 4;
            case DONE          -> TOTAL_STEPS + 1;
        };
    }

    /** Returns {@code true} once all four steps are complete. */
    public boolean isDone() { return current == Step.DONE; }

    /**
     * Returns and clears all {@link TutorialStepComplete} events accumulated since the last drain.
     * Safe to call every frame; returns an empty list if no step advanced this tick.
     */
    public List<TutorialStepComplete> drainUiEvents() {
        if (uiEvents.isEmpty()) {
            return List.of();
        }
        List<TutorialStepComplete> copy = List.copyOf(uiEvents);
        uiEvents.clear();
        return copy;
    }

    // ---- Private helpers --------------------------------------------------------------------

    private void advance() {
        Step from = current;
        current = Step.values()[current.ordinal() + 1];
        uiEvents.add(new TutorialStepComplete(from, current));
    }

    private static TankSnapshot playerTank(GameSnapshot snapshot) {
        for (TankSnapshot t : snapshot.tanks()) {
            if (t.playerId() == 0) {
                return t;
            }
        }
        return null;
    }
}
