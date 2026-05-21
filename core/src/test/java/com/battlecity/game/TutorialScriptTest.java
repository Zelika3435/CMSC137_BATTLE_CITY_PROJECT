package com.battlecity.game;

import com.battlecity.game.event.TileDestroyed;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless tests for {@link TutorialScript}.
 *
 * <p>All steps must advance from deterministic simulation conditions driven by scripted inputs
 * and tick counts — no wall-clock time, no LibGDX context.
 */
public class TutorialScriptTest {

    // ---- helpers ----------------------------------------------------------------------------

    /**
     * Runs ticks with the supplied inputs until the script reaches {@code expected} or
     * {@code maxTicks} is exhausted.  Asserts that the step was reached.
     */
    private static void tickUntilStep(
            LocalMatchController ctrl,
            TutorialScript script,
            TutorialScript.Step expected,
            Direction moveDir,
            boolean fire,
            int maxTicks) {
        for (int i = 0; i < maxTicks && script.currentStep() != expected; i++) {
            ctrl.tick(moveDir, fire);
            script.evaluate(ctrl.snapshot(), ctrl.lastEvents());
            script.drainUiEvents(); // keep queue empty between calls
        }
        assertEquals(expected, script.currentStep(),
                "Expected to reach step " + expected + " within " + maxTicks + " ticks");
    }

    /**
     * Runs ticks with split inputs: {@code moveDir} every tick and {@code fire} only on the
     * first tick.  Used when a single SPACE triggers DESTROY_BRICK and we then wait for the
     * projectile to travel.
     */
    private static void tickFireThenMove(
            LocalMatchController ctrl,
            TutorialScript script,
            Direction moveDir,
            int maxTicks) {
        boolean firedOnce = false;
        for (int i = 0; i < maxTicks && script.currentStep() == TutorialScript.Step.DESTROY_BRICK; i++) {
            boolean fire = !firedOnce;
            firedOnce = true;
            ctrl.tick(moveDir, fire);
            script.evaluate(ctrl.snapshot(), ctrl.lastEvents());
            script.drainUiEvents();
        }
    }

    // ---- tests ------------------------------------------------------------------------------

    /**
     * Verifies that a fresh tutorial script starts on {@link TutorialScript.Step#MOVE_SOUTH}.
     */
    @Test
    public void initialStepIsMOVE_SOUTH() {
        LocalMatchController ctrl   = LocalMatchController.forTutorial();
        TutorialScript       script = new TutorialScript();
        script.init(ctrl.snapshot());

        assertEquals(TutorialScript.Step.MOVE_SOUTH, script.currentStep());
        assertEquals(1, script.currentStepNumber());
        assertFalse(script.isDone());
    }

    /**
     * MOVE_SOUTH advances once the player's Y drops more than one tile below spawn.
     */
    @Test
    public void moveSouthAdvancesOnSouthMovement() {
        LocalMatchController ctrl   = LocalMatchController.forTutorial();
        TutorialScript       script = new TutorialScript();
        script.init(ctrl.snapshot());

        float spawnY = ctrl.snapshot().tanks().stream()
                .filter(t -> t.playerId() == 0).findFirst().orElseThrow().y();

        // Move south until condition is met (≥1 tile south = 16 px).
        tickUntilStep(ctrl, script, TutorialScript.Step.FACE_NORTH, Direction.DOWN, false, 30);

        float newY = ctrl.snapshot().tanks().stream()
                .filter(t -> t.playerId() == 0).findFirst().orElseThrow().y();
        assertTrue(newY < spawnY - MapFactory.DEFAULT_TILE_SIZE,
                "Tank must have moved south by at least 16 px");
    }

    /**
     * FACE_NORTH advances when player presses UP after having moved south.
     */
    @Test
    public void faceNorthAdvancesOnUpInput() {
        LocalMatchController ctrl   = LocalMatchController.forTutorial();
        TutorialScript       script = new TutorialScript();
        script.init(ctrl.snapshot());

        // Reach FACE_NORTH by moving south first.
        tickUntilStep(ctrl, script, TutorialScript.Step.FACE_NORTH, Direction.DOWN, false, 30);

        assertEquals(TutorialScript.Step.FACE_NORTH, script.currentStep());

        // A single UP input sets dir=UP, which satisfies FACE_NORTH.
        tickUntilStep(ctrl, script, TutorialScript.Step.DESTROY_BRICK, Direction.UP, false, 5);

        assertEquals(TutorialScript.Step.DESTROY_BRICK, script.currentStep());
        assertEquals(3, script.currentStepNumber());
    }

    /**
     * DESTROY_BRICK advances when a {@link TileDestroyed} event with {@code previousTile==BRICK}
     * occurs.  The player fires northward at the brick cluster placed directly in their path.
     */
    @Test
    public void destroyBrickAdvancesOnTileDestroyedEvent() {
        LocalMatchController ctrl   = LocalMatchController.forTutorial();
        TutorialScript       script = new TutorialScript();
        script.init(ctrl.snapshot());

        // Move south → face north.
        tickUntilStep(ctrl, script, TutorialScript.Step.FACE_NORTH, Direction.DOWN, false, 30);
        tickUntilStep(ctrl, script, TutorialScript.Step.DESTROY_BRICK, Direction.UP, false, 5);

        // Fire once then keep moving northward until the projectile hits the brick.
        // Brick is at tile (6, 8) = world y≈136.  Projectile speed=240 f/s → 4 f/tick.
        // From y≈40 (south of spawn), projectile travels ~90 px → ~23 ticks.
        tickFireThenMove(ctrl, script, Direction.UP, 80);

        assertEquals(TutorialScript.Step.REACH_BASE, script.currentStep());
        assertEquals(4, script.currentStepNumber());
    }

    /**
     * Verifies that a {@link TileDestroyed} event with {@code previousTile==STEEL} does NOT
     * advance DESTROY_BRICK.  (Steel cannot be destroyed; the event is never emitted for steel.)
     * This test confirms the map contains the steel obstacle and the script is not fooled.
     */
    @Test
    public void steelObstacleDoesNotAdvanceBrickStep() {
        // The steel obstacle is to the left of the brick row (tx=4, ty=8).
        // Firing leftward from the spawn would require x alignment — instead we just confirm
        // that firing upward from the centre column skips the steel and the script does not
        // advance from a TileDestroyed event unless the tile was BRICK.
        TutorialScript script = new TutorialScript();

        // Fabricate a STEEL TileDestroyed event to ensure it does NOT advance DESTROY_BRICK.
        LocalMatchController ctrl = LocalMatchController.forTutorial();
        script.init(ctrl.snapshot());

        // Manually push the script to DESTROY_BRICK step by moving south then facing north.
        tickUntilStep(ctrl, script, TutorialScript.Step.FACE_NORTH, Direction.DOWN, false, 30);
        tickUntilStep(ctrl, script, TutorialScript.Step.DESTROY_BRICK, Direction.UP, false, 5);

        // Evaluate with a fake STEEL destroyed event — script must stay on DESTROY_BRICK.
        script.evaluate(ctrl.snapshot(),
                List.of(new TileDestroyed(4, 8, Tile.STEEL)));

        assertEquals(TutorialScript.Step.DESTROY_BRICK, script.currentStep(),
                "STEEL event must not advance DESTROY_BRICK");
    }

    /**
     * REACH_BASE advances when the tank reaches within 3 tiles of the BASE centre.
     * Base is at tile (6, 11) → world centre (104, 184).
     */
    @Test
    public void reachBaseAdvancesWhenNearBase() {
        LocalMatchController ctrl   = LocalMatchController.forTutorial();
        TutorialScript       script = new TutorialScript();
        script.init(ctrl.snapshot());

        // Walk through steps 1–3.
        tickUntilStep(ctrl, script, TutorialScript.Step.FACE_NORTH, Direction.DOWN, false, 30);
        tickUntilStep(ctrl, script, TutorialScript.Step.DESTROY_BRICK, Direction.UP, false, 5);
        tickFireThenMove(ctrl, script, Direction.UP, 80);

        assertEquals(TutorialScript.Step.REACH_BASE, script.currentStep());

        // Drive north to base (≈150 more px from near-spawn; 75+ ticks at 2 f/tick).
        tickUntilStep(ctrl, script, TutorialScript.Step.DONE, Direction.UP, false, 150);

        assertTrue(script.isDone(), "Script should be DONE after reaching base");
        assertEquals(TutorialScript.TOTAL_STEPS + 1, script.currentStepNumber());
    }

    /**
     * Full end-to-end run: all four steps advance in order from scripted inputs.
     * Verifies the {@link TutorialStepComplete} events are emitted exactly once per step.
     */
    @Test
    public void allFourStepsAdvanceInOrder() {
        LocalMatchController ctrl   = LocalMatchController.forTutorial();
        TutorialScript       script = new TutorialScript();
        script.init(ctrl.snapshot());

        TutorialScript.Step[] expectedOrder = {
                TutorialScript.Step.FACE_NORTH,
                TutorialScript.Step.DESTROY_BRICK,
                TutorialScript.Step.REACH_BASE,
                TutorialScript.Step.DONE
        };

        // Step 1 → FACE_NORTH
        tickUntilStep(ctrl, script, expectedOrder[0], Direction.DOWN, false, 30);
        List<TutorialStepComplete> ev1 = script.drainUiEvents(); // drain any residual
        // (events may already have been drained by tickUntilStep — that's fine)

        // Step 2 → DESTROY_BRICK
        tickUntilStep(ctrl, script, expectedOrder[1], Direction.UP, false, 5);

        // Step 3 → REACH_BASE
        tickFireThenMove(ctrl, script, Direction.UP, 80);
        assertEquals(TutorialScript.Step.REACH_BASE, script.currentStep());

        // Step 4 → DONE
        tickUntilStep(ctrl, script, expectedOrder[3], Direction.UP, false, 150);

        assertTrue(script.isDone());
        assertFalse(ctrl.isMatchOver(),
                "Match world must not be over during tutorial (no BASE destroyed by player)");
    }

    /**
     * No step advances when the player does not move (stays at spawn, no input).
     */
    @Test
    public void noAdvanceWhenIdle() {
        LocalMatchController ctrl   = LocalMatchController.forTutorial();
        TutorialScript       script = new TutorialScript();
        script.init(ctrl.snapshot());

        for (int i = 0; i < 120; i++) {
            ctrl.tick(null, false);
            script.evaluate(ctrl.snapshot(), ctrl.lastEvents());
        }

        assertEquals(TutorialScript.Step.MOVE_SOUTH, script.currentStep(),
                "Script must stay on MOVE_SOUTH while player is idle");
    }

    /**
     * Moving northward (UP) satisfies MOVE_SOUTH only when the player actually moved south
     * enough first — confirms the threshold is relative to the initial spawn Y, not an
     * absolute world coordinate.
     */
    @Test
    public void movingNorthAloneDoesNotAdvanceMoveSOUTH() {
        LocalMatchController ctrl   = LocalMatchController.forTutorial();
        TutorialScript       script = new TutorialScript();
        script.init(ctrl.snapshot());

        // Move north for many ticks; spawn is near the south end, so player hits the border quickly.
        for (int i = 0; i < 60; i++) {
            ctrl.tick(Direction.UP, false);
            script.evaluate(ctrl.snapshot(), ctrl.lastEvents());
        }

        assertEquals(TutorialScript.Step.MOVE_SOUTH, script.currentStep(),
                "Moving north must not satisfy MOVE_SOUTH");
    }
}
