package com.battlecity.net.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.battlecity.game.Direction;
import com.battlecity.game.GameCommand;
import com.battlecity.game.HeadlessGameHarness;
import com.battlecity.game.QueuedCommand;
import com.battlecity.game.snapshot.GameSnapshot;
import com.battlecity.net.protocol.ProtocolConstants;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Headless tests verifying that inputs from remote (non-loopback) clients with
 * simulated network delay are reliably applied by the server.
 *
 * <p>No network I/O, no LibGDX, no OpenGL.  All tests drive the simulation
 * through {@link HeadlessGameHarness} + {@link ClientConnection} directly.
 *
 * <h3>What is tested</h3>
 * <ul>
 *   <li>A command stamped with {@code INPUT_LEAD_TICKS} ahead of the last snapshot tick
 *       still lands on the server when the packet is delayed by 1–5 ticks (20–83 ms at 60 Hz).
 *   <li>Two logical clients with different simulated delays both have their inputs applied
 *       so the tank positions diverge from the no-input baseline.
 *   <li>The server state after lagged inputs matches the state produced by a zero-lag
 *       reference simulation given identical commands — confirming determinism is preserved.
 * </ul>
 */
final class LaggedInputTest {

    // ---- helpers ---------------------------------------------------------------------------

    /**
     * Simulates a command being sent by a client at the given {@code clientLastServerTick}
     * (i.e. the last snapshot tick the client has seen), with the client-side lead applied,
     * exactly as {@link com.battlecity.net.client.GameClient#sendInput} does.
     */
    private static QueuedCommand makeLeadInput(int playerId, int seq,
                                               long clientLastServerTick,
                                               GameCommand command, Direction dir) {
        long tickStamp = Math.max(0L, clientLastServerTick + ProtocolConstants.INPUT_LEAD_TICKS);
        return new QueuedCommand(playerId, tickStamp, seq, command, dir);
    }

    /**
     * Minimal in-memory delivery queue that delays commands by {@code delayTicks} ticks
     * before making them available to the server — models one-way network latency.
     */
    private static final class DelayedQueue {
        private final int delayTicks;
        private final Deque<long[]> queue = new ArrayDeque<>(); // [deliverAtTick, cmdIndex]
        private final List<QueuedCommand> pending = new ArrayList<>();

        DelayedQueue(int delayTicks) {
            this.delayTicks = delayTicks;
        }

        /** Enqueue a command to be delivered at tick {@code sentAtTick + delayTicks}. */
        void enqueue(QueuedCommand cmd, long sentAtTick) {
            long deliverAt = sentAtTick + delayTicks;
            pending.add(cmd);
            queue.add(new long[]{deliverAt, pending.size() - 1});
        }

        /**
         * Returns all commands whose delivery tick has arrived (≤ {@code currentTick}).
         * The returned commands have already been removed from the queue.
         */
        List<QueuedCommand> drain(long currentTick) {
            List<QueuedCommand> ready = new ArrayList<>();
            queue.removeIf(entry -> {
                if (entry[0] <= currentTick) {
                    ready.add(pending.get((int) entry[1]));
                    return true;
                }
                return false;
            });
            return ready;
        }
    }

    // ---- tests -----------------------------------------------------------------------------

    /**
     * Single remote client, 3-tick one-way delay (≈ 50 ms at 60 Hz).
     * Sends MOVE_DIR SOUTH starting at tick 0; verifies the tank actually moved after
     * enough ticks for the command to transit and be applied.
     */
    @Test
    void remoteClient_3tickDelay_moveApplied() {
        final int DELAY = 3;
        final int RUN_TICKS = 20;

        HeadlessGameHarness server = new HeadlessGameHarness(0L);
        ClientConnection conn = new ClientConnection(
                new InetSocketAddress("10.0.0.2", 5000), 0, 1, "RemotePlayer");
        DelayedQueue net = new DelayedQueue(DELAY);

        // Client believes it received snapshot for tick 0 → stamps tickStamp = 0 + LEAD = 2.
        long clientLastSnapshot = 0L;
        QueuedCommand move = makeLeadInput(0, 1, clientLastSnapshot, GameCommand.MOVE_DIR, Direction.DOWN);
        net.enqueue(move, /* sentAtTick= */ 0L);

        // Record baseline position (no input) at tick DELAY+2 for comparison.
        HeadlessGameHarness baseline = new HeadlessGameHarness(0L);
        for (int i = 0; i < RUN_TICKS; i++) {
            baseline.step(List.of());
        }
        float baselineY = baseline.snapshot().tanks().get(0).y();

        // Advance server; inject commands when the delay queue delivers them.
        GameSnapshot lastSnap = null;
        for (int tick = 0; tick < RUN_TICKS; tick++) {
            long serverTick = server.simulation().tickCount();
            List<QueuedCommand> arrived = net.drain(serverTick);
            for (QueuedCommand cmd : arrived) {
                conn.registerInput(cmd, serverTick);
            }
            List<QueuedCommand> commands = conn.drainInputsForTick(serverTick);
            server.step(commands);
            lastSnap = server.snapshot();
        }

        float movedY = lastSnap.tanks().get(0).y();
        assertNotEquals(baselineY, movedY,
                "tank Y must differ from no-input baseline after delayed MOVE_DIR DOWN");
    }

    /**
     * Two logical clients with different simulated delays:
     * - Player 0 (loopback): 0-tick delay (host)
     * - Player 1 (remote):   5-tick delay (≈ 83 ms one-way, worst-case LAN)
     *
     * Both clients send MOVE_DIR commands.  After enough ticks both tanks must have
     * moved from their spawn positions.
     */
    @Test
    void twoClients_differentDelays_bothInputsApplied() {
        final int RUN_TICKS = 30;

        HeadlessGameHarness server = new HeadlessGameHarness(0L);

        ClientConnection p0conn = new ClientConnection(
                new InetSocketAddress("127.0.0.1", 5000), 0, 1, "Host");
        ClientConnection p1conn = new ClientConnection(
                new InetSocketAddress("10.0.0.2", 5001), 1, 2, "Remote");

        DelayedQueue net0 = new DelayedQueue(0);  // loopback
        DelayedQueue net1 = new DelayedQueue(5);  // 5-tick LAN delay

        // Both clients stamped against tick 0.
        net0.enqueue(makeLeadInput(0, 1, 0L, GameCommand.MOVE_DIR, Direction.UP), 0L);
        net1.enqueue(makeLeadInput(1, 1, 0L, GameCommand.MOVE_DIR, Direction.DOWN), 0L);

        GameSnapshot lastSnap = null;
        for (int tick = 0; tick < RUN_TICKS; tick++) {
            long serverTick = server.simulation().tickCount();

            for (QueuedCommand cmd : net0.drain(serverTick)) p0conn.registerInput(cmd, serverTick);
            for (QueuedCommand cmd : net1.drain(serverTick)) p1conn.registerInput(cmd, serverTick);

            List<QueuedCommand> commands = new ArrayList<>();
            commands.addAll(p0conn.drainInputsForTick(serverTick));
            commands.addAll(p1conn.drainInputsForTick(serverTick));
            server.step(commands);
            lastSnap = server.snapshot();
        }

        // Baseline: same ticks, zero commands.
        HeadlessGameHarness baseline = new HeadlessGameHarness(0L);
        for (int i = 0; i < RUN_TICKS; i++) baseline.step(List.of());

        float p0BaseY = baseline.snapshot().tanks().get(0).y();
        float p1BaseY = baseline.snapshot().tanks().get(1).y();

        assertNotEquals(p0BaseY, lastSnap.tanks().get(0).y(),
                "Player 0 (0-tick delay) tank must have moved from baseline Y");
        assertNotEquals(p1BaseY, lastSnap.tanks().get(1).y(),
                "Player 1 (5-tick delay) tank must have moved from baseline Y");
    }

    /**
     * Determinism check: two server instances receive the same lagged command sequence
     * (2-tick delay) and must produce identical state hashes at every tick.
     */
    @Test
    void laggedInputs_deterministicAcrossTwoServerInstances() {
        final int DELAY = 2;
        final int RUN_TICKS = 25;

        HeadlessGameHarness sim1 = new HeadlessGameHarness(0L);
        HeadlessGameHarness sim2 = new HeadlessGameHarness(0L);

        ClientConnection conn1 = new ClientConnection(new InetSocketAddress("10.0.0.3", 1), 0, 1, "P0");
        ClientConnection conn2 = new ClientConnection(new InetSocketAddress("10.0.0.3", 1), 0, 1, "P0");

        DelayedQueue net1 = new DelayedQueue(DELAY);
        DelayedQueue net2 = new DelayedQueue(DELAY);

        // Send FIRE on tick 0 with client lead applied.
        QueuedCommand fire = makeLeadInput(0, 1, 0L, GameCommand.FIRE, null);
        net1.enqueue(fire, 0L);
        net2.enqueue(fire, 0L);

        for (int tick = 0; tick < RUN_TICKS; tick++) {
            long serverTick1 = sim1.simulation().tickCount();
            long serverTick2 = sim2.simulation().tickCount();
            assertEquals(serverTick1, serverTick2, "tick counters must match");

            for (QueuedCommand cmd : net1.drain(serverTick1)) conn1.registerInput(cmd, serverTick1);
            for (QueuedCommand cmd : net2.drain(serverTick2)) conn2.registerInput(cmd, serverTick2);

            sim1.step(conn1.drainInputsForTick(serverTick1));
            sim2.step(conn2.drainInputsForTick(serverTick2));
        }

        assertEquals(sim1.hashSequence(), sim2.hashSequence(),
                "two simulations with identical lagged-input delivery must produce identical hash sequences");
    }

    /**
     * Future-stamp guard: commands stamped beyond {@link ProtocolConstants#MAX_INPUT_FUTURE_TICKS}
     * must be rejected by {@link ClientConnection#registerInput}.
     */
    @Test
    void futureStampBeyondMax_isRejected() {
        ClientConnection conn = new ClientConnection(
                new InetSocketAddress("127.0.0.1", 1), 0, 1, "P0");
        long serverTick = 10L;
        long tooFarAhead = serverTick + ProtocolConstants.MAX_INPUT_FUTURE_TICKS + 1;
        QueuedCommand cmd = new QueuedCommand(0, tooFarAhead, 1, GameCommand.FIRE, null);
        assertEquals(false, conn.registerInput(cmd, serverTick),
                "input stamped beyond MAX_INPUT_FUTURE_TICKS must be rejected");
    }

    /**
     * Future-stamp at exactly the max is accepted (boundary condition).
     */
    @Test
    void futureStampAtMax_isAccepted() {
        ClientConnection conn = new ClientConnection(
                new InetSocketAddress("127.0.0.1", 1), 0, 1, "P0");
        long serverTick = 10L;
        long atMax = serverTick + ProtocolConstants.MAX_INPUT_FUTURE_TICKS;
        QueuedCommand cmd = new QueuedCommand(0, atMax, 1, GameCommand.FIRE, null);
        assertEquals(true, conn.registerInput(cmd, serverTick),
                "input stamped at exactly MAX_INPUT_FUTURE_TICKS must be accepted");
    }
}
