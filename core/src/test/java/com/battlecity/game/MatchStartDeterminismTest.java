package com.battlecity.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies that two simulations seeded identically and given identical inputs produce the same
 * {@link StateHasher} sequence after the server transitions to {@link com.battlecity.net.server.ServerPhase#RUNNING}.
 *
 * <p>All simulations are created exactly as {@link com.battlecity.net.server.GameServer} does on
 * RUNNING entry: {@code new Simulation(World.createDefault(), false, 0L)} at tick 0.  No network,
 * no LibGDX, no OpenGL is used.
 */
final class MatchStartDeterminismTest {

    /** Ticks to simulate (2 seconds at 60 Hz). */
    private static final int SIM_TICKS = 120;

    // ---- Identical input tests --------------------------------------------------------------

    @Test
    void twoPlayerIdenticalInputs_sameHashSequence() {
        List<List<QueuedCommand>> schedule = twoPlayerMoveFireSchedule(SIM_TICKS);

        HeadlessGameHarness a = new HeadlessGameHarness(0L);
        HeadlessGameHarness b = new HeadlessGameHarness(0L);
        for (int i = 0; i < SIM_TICKS; i++) {
            a.step(schedule.get(i));
            b.step(schedule.get(i));
        }

        assertEquals(a.hashSequence(), b.hashSequence(),
                "two sims with identical inputs must produce identical hash sequences");
    }

    @Test
    void fourPlayerIdenticalInputs_sameHashSequence() {
        // All 4 players active and issuing commands to stress the tick order.
        List<List<QueuedCommand>> schedule = fourPlayerMixedSchedule(SIM_TICKS);

        HeadlessGameHarness a = new HeadlessGameHarness(0L);
        HeadlessGameHarness b = new HeadlessGameHarness(0L);
        for (int i = 0; i < SIM_TICKS; i++) {
            a.step(schedule.get(i));
            b.step(schedule.get(i));
        }

        assertEquals(a.hashSequence(), b.hashSequence(),
                "four-player identical inputs must produce identical hash sequences");
    }

    @Test
    void idleSimulation_sameHashSequence() {
        // No commands at all — pure determinism of the initial world.
        HeadlessGameHarness a = new HeadlessGameHarness(0L);
        HeadlessGameHarness b = new HeadlessGameHarness(0L);
        for (int i = 0; i < SIM_TICKS; i++) {
            a.step(List.of());
            b.step(List.of());
        }
        assertEquals(a.hashSequence(), b.hashSequence(),
                "idle sims with no commands must remain in sync");
    }

    // ---- Initial state determinism ----------------------------------------------------------

    @Test
    void tickZeroHash_identicalAcrossTwoFreshSimulations() {
        // Before any tick is applied, both worlds must already be in an identical state.
        HeadlessGameHarness a = new HeadlessGameHarness(0L);
        HeadlessGameHarness b = new HeadlessGameHarness(0L);
        assertEquals(a.lastHash(), b.lastHash(),
                "fresh simulations at tick 0 must have identical state hash");
    }

    // ---- Desync detection -------------------------------------------------------------------

    @Test
    void divergentCommand_atTickZero_producesHashMismatch() {
        HeadlessGameHarness a = new HeadlessGameHarness(0L);
        HeadlessGameHarness b = new HeadlessGameHarness(0L);

        // Tick 0: a moves right, b moves left.
        a.step(List.of(new QueuedCommand(0, 0L, 1, GameCommand.MOVE_DIR, Direction.RIGHT)));
        b.step(List.of(new QueuedCommand(0, 0L, 1, GameCommand.MOVE_DIR, Direction.LEFT)));

        assertNotEquals(a.lastHash(), b.lastHash(),
                "single divergent command at tick 0 must produce an immediate hash mismatch");
    }

    @Test
    void divergentFire_producesHashMismatch_beforeProjectileExpires() {
        // One sim fires at tick 30, the other never fires.
        HeadlessGameHarness a = new HeadlessGameHarness(0L);
        HeadlessGameHarness b = new HeadlessGameHarness(0L);

        for (int i = 0; i < SIM_TICKS; i++) {
            long tick = (long) i;
            if (i == 30) {
                a.step(List.of(new QueuedCommand(0, tick, i + 1, GameCommand.FIRE, null)));
                b.step(List.of()); // b never fires
            } else {
                a.step(List.of());
                b.step(List.of());
            }
        }

        assertNotEquals(a.hashSequence(), b.hashSequence(),
                "sim that fired must diverge from sim that did not");
    }

    @Test
    void missingCommand_atOneTick_isDetectedLater() {
        // Both sims start identically, then sim B drops one move command at tick 10.
        List<List<QueuedCommand>> schedule = twoPlayerMoveFireSchedule(SIM_TICKS);

        HeadlessGameHarness a = new HeadlessGameHarness(0L);
        HeadlessGameHarness b = new HeadlessGameHarness(0L);
        for (int i = 0; i < SIM_TICKS; i++) {
            a.step(schedule.get(i));
            // B drops tick 10's commands entirely (simulates a lost UDP packet).
            b.step(i == 10 ? List.of() : schedule.get(i));
        }

        assertNotEquals(a.hashSequence(), b.hashSequence(),
                "dropped command must cause hash divergence");
    }

    // ---- Resync after forced alignment ------------------------------------------------------

    @Test
    void replayFromSameSnapshot_reconverges() {
        // Simulate a server snapshot resync: both sims are reset to the same world state
        // (as if the client received a full snapshot) and then run identically.
        HeadlessGameHarness a = new HeadlessGameHarness(0L);
        HeadlessGameHarness b = new HeadlessGameHarness(0L);

        // First 30 ticks: b diverges.
        for (int i = 0; i < 30; i++) {
            long tick = (long) i;
            a.step(List.of(new QueuedCommand(0, tick, i + 1, GameCommand.MOVE_DIR, Direction.UP)));
            b.step(List.of());
        }
        assertNotEquals(a.lastHash(), b.lastHash(), "pre-condition: sims must have diverged");

        // "Resync": recreate both from the same initial world (tick 0), simulating a
        // server-authoritative snapshot that resets client state.
        HeadlessGameHarness syncA = new HeadlessGameHarness(0L);
        HeadlessGameHarness syncB = new HeadlessGameHarness(0L);

        List<List<QueuedCommand>> schedule = twoPlayerMoveFireSchedule(SIM_TICKS);
        for (int i = 0; i < SIM_TICKS; i++) {
            syncA.step(schedule.get(i));
            syncB.step(schedule.get(i));
        }

        assertEquals(syncA.hashSequence(), syncB.hashSequence(),
                "after resync from identical state, sims must converge again");
    }

    // ---- Command-schedule builders ---------------------------------------------------------

    /**
     * Builds a per-tick command list for 2 players.
     * Player 0 moves RIGHT; player 1 moves LEFT; both fire every 30 ticks.
     */
    private static List<List<QueuedCommand>> twoPlayerMoveFireSchedule(int ticks) {
        List<List<QueuedCommand>> schedule = new ArrayList<>(ticks);
        for (int i = 0; i < ticks; i++) {
            List<QueuedCommand> tick = new ArrayList<>();
            tick.add(new QueuedCommand(0, (long) i, i + 1, GameCommand.MOVE_DIR, Direction.RIGHT));
            tick.add(new QueuedCommand(1, (long) i, i + 1, GameCommand.MOVE_DIR, Direction.LEFT));
            if (i % 30 == 0) {
                tick.add(new QueuedCommand(0, (long) i, i + 1001, GameCommand.FIRE, null));
            }
            schedule.add(List.copyOf(tick));
        }
        return schedule;
    }

    /**
     * Builds a per-tick command list for all 4 players with mixed movement directions and
     * staggered FIRE commands, stressing command-ordering determinism.
     */
    private static List<List<QueuedCommand>> fourPlayerMixedSchedule(int ticks) {
        Direction[] dirs = {Direction.RIGHT, Direction.LEFT, Direction.UP, Direction.DOWN};
        List<List<QueuedCommand>> schedule = new ArrayList<>(ticks);
        for (int i = 0; i < ticks; i++) {
            List<QueuedCommand> tick = new ArrayList<>();
            for (int pid = 0; pid < 4; pid++) {
                tick.add(new QueuedCommand(pid, (long) i, i + (pid * 1000) + 1,
                        GameCommand.MOVE_DIR, dirs[pid % dirs.length]));
                // Stagger fire commands so not all players fire simultaneously.
                if (i % 20 == pid * 5) {
                    tick.add(new QueuedCommand(pid, (long) i, i + (pid * 1000) + 2001,
                            GameCommand.FIRE, null));
                }
            }
            schedule.add(List.copyOf(tick));
        }
        return schedule;
    }
}
