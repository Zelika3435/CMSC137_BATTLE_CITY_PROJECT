package com.battlecity.net.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.battlecity.game.Direction;
import com.battlecity.game.GameCommand;
import com.battlecity.game.HeadlessGameHarness;
import com.battlecity.game.QueuedCommand;
import com.battlecity.game.snapshot.GameSnapshot;
import com.battlecity.net.protocol.MessageCodec;
import com.battlecity.net.protocol.MessageType;
import com.battlecity.net.protocol.NetMessages;
import com.battlecity.net.protocol.PacketHeader;
import com.battlecity.net.protocol.ProtocolConstants;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Headless multiplayer fairness: host (0-tick delay) vs joiner (~50 ms one-way on INPUT and
 * SNAPSHOT) over 300 ticks of the same key sequence.
 *
 * <p>No OpenGL, no {@link Thread#sleep}. Mirrors {@link GameServer} RUNNING input handling
 * ({@link ClientConnection}) and {@link com.battlecity.net.client.GameClient} tick stamping
 * ({@link ProtocolConstants#INPUT_LEAD_TICKS}) with tick-based delivery queues.
 */
final class HeadlessMultiplayerFairnessTest {

    /** ~50 ms one-way at 60 Hz (50 / 16.67 ms). */
    private static final int JOINER_ONE_WAY_DELAY_TICKS = 3;
    private static final int RUN_TICKS = 300;
    private static final int FIRE_INTERVAL_TICKS = 60;

    @Test
    void joiner50msDelay_identicalKeySequence_affectsServerState() {
        HeadlessGameHarness server = new HeadlessGameHarness(0L);
        ClientConnection hostConn = new ClientConnection(
                new InetSocketAddress("127.0.0.1", 5000), 0, 1, "Host");
        ClientConnection joinerConn = new ClientConnection(
                new InetSocketAddress("10.0.0.2", 5001), 1, 2, "Joiner");

        TickDelayedQueue hostInputs = new TickDelayedQueue(0);
        TickDelayedQueue joinerInputs = new TickDelayedQueue(JOINER_ONE_WAY_DELAY_TICKS);
        TickDelayedSnapshots joinerSnapshots = new TickDelayedSnapshots(JOINER_ONE_WAY_DELAY_TICKS);

        long hostLastSnap = 0L;
        long joinerLastSnap = 0L;
        int seq = 0;

        GameSnapshot lastSnap = null;
        for (int tick = 0; tick < RUN_TICKS; tick++) {
            long serverTick = server.simulation().tickCount();

            seq++;
            hostInputs.enqueue(codecInput(hostConn, seq, hostLastSnap, GameCommand.MOVE_DIR, Direction.RIGHT),
                    tick);
            joinerInputs.enqueue(codecInput(joinerConn, seq, joinerLastSnap, GameCommand.MOVE_DIR, Direction.RIGHT),
                    tick);
            if (tick > 0 && tick % FIRE_INTERVAL_TICKS == 0) {
                seq++;
                hostInputs.enqueue(codecInput(hostConn, seq, hostLastSnap, GameCommand.FIRE, null), tick);
                joinerInputs.enqueue(codecInput(joinerConn, seq, joinerLastSnap, GameCommand.FIRE, null), tick);
            }

            deliverInputs(serverTick, hostInputs, hostConn);
            deliverInputs(serverTick, joinerInputs, joinerConn);
            server.step(collectDrained(hostConn, joinerConn, serverTick));

            lastSnap = server.snapshot();
            hostLastSnap = lastSnap.serverTick();
            joinerSnapshots.enqueue(lastSnap, tick);
            for (GameSnapshot delivered : joinerSnapshots.drain(tick)) {
                joinerLastSnap = delivered.serverTick();
            }
        }

        HeadlessGameHarness baseline = new HeadlessGameHarness(0L);
        for (int i = 0; i < RUN_TICKS; i++) {
            baseline.step(List.of());
        }
        float joinerBaselineX = baseline.snapshot().tanks().get(1).x();

        float joinerFinalX = lastSnap.tanks().get(1).x();
        assertNotEquals(joinerBaselineX, joinerFinalX,
                "joiner delayed MOVE_DIR/FIRE sequence must affect authoritative tank state (not silently dropped)");
    }

    @Test
    void hostZeroDelay_sameInputs_hashSequenceMatchesAuthoritativeServer() {
        HeadlessGameHarness server = new HeadlessGameHarness(0L);
        HeadlessGameHarness reference = new HeadlessGameHarness(0L);
        ClientConnection hostConn = new ClientConnection(
                new InetSocketAddress("127.0.0.1", 5000), 0, 1, "Host");

        TickDelayedQueue hostInputs = new TickDelayedQueue(0);
        long hostLastSnap = 0L;
        int seq = 0;

        for (int tick = 0; tick < RUN_TICKS; tick++) {
            long serverTick = server.simulation().tickCount();

            seq++;
            QueuedCommand move = codecInput(hostConn, seq, hostLastSnap, GameCommand.MOVE_DIR, Direction.RIGHT);
            hostInputs.enqueue(move, tick);
            List<QueuedCommand> tickCommands = new ArrayList<>();
            if (tick > 0 && tick % FIRE_INTERVAL_TICKS == 0) {
                seq++;
                tickCommands.add(codecInput(hostConn, seq, hostLastSnap, GameCommand.FIRE, null));
            }

            deliverInputs(serverTick, hostInputs, hostConn);
            tickCommands.addAll(hostConn.drainInputsForTick(serverTick));
            server.step(tickCommands);
            reference.step(tickCommands);

            hostLastSnap = server.snapshot().serverTick();
        }

        assertEquals(reference.hashSequence(), server.hashSequence(),
                "zero-delay host path must match direct authoritative stepping");
    }

    private static void deliverInputs(long serverTick, TickDelayedQueue net, ClientConnection conn) {
        for (QueuedCommand cmd : net.drain(serverTick)) {
            conn.registerInput(cmd, serverTick);
        }
    }

    private static List<QueuedCommand> collectDrained(
            ClientConnection host, ClientConnection joiner, long serverTick) {
        List<QueuedCommand> commands = new ArrayList<>();
        commands.addAll(host.drainInputsForTick(serverTick));
        commands.addAll(joiner.drainInputsForTick(serverTick));
        return commands;
    }

    /**
     * Builds a command the way {@link com.battlecity.net.client.GameClient} stamps and sends it,
     * with a MessageCodec roundtrip on the wire payload.
     */
    private static QueuedCommand codecInput(
            ClientConnection conn,
            int seq,
            long clientLastServerTick,
            GameCommand command,
            Direction moveDir) {
        long tickStamp = Math.max(0L, clientLastServerTick + ProtocolConstants.INPUT_LEAD_TICKS);
        NetMessages.InputPayload payload = new NetMessages.InputPayload(tickStamp, command, moveDir);
        PacketHeader header = new PacketHeader(
                ProtocolConstants.PROTOCOL_VERSION,
                MessageType.INPUT,
                conn.sessionId,
                conn.playerId,
                seq,
                0,
                clientLastServerTick
        );
        NetMessages.NetPacket packet = new NetMessages.NetPacket(header, payload);
        NetMessages.NetPacket decoded = MessageCodec.decode(MessageCodec.encode(packet));
        NetMessages.InputPayload wire = (NetMessages.InputPayload) decoded.payload();
        return new QueuedCommand(
                conn.playerId,
                wire.tickStamp(),
                seq,
                wire.command(),
                wire.moveDir());
    }

    /** One-way latency modeled in whole server ticks (no wall clock). */
    private static final class TickDelayedQueue {
        private final int delayTicks;
        private final Deque<long[]> queue = new ArrayDeque<>();
        private final List<QueuedCommand> pending = new ArrayList<>();

        TickDelayedQueue(int delayTicks) {
            this.delayTicks = delayTicks;
        }

        void enqueue(QueuedCommand cmd, long sentAtTick) {
            pending.add(cmd);
            queue.add(new long[]{sentAtTick + delayTicks, pending.size() - 1L});
        }

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

    private static final class TickDelayedSnapshots {
        private final int delayTicks;
        private final Deque<long[]> queue = new ArrayDeque<>();
        private final List<GameSnapshot> pending = new ArrayList<>();

        TickDelayedSnapshots(int delayTicks) {
            this.delayTicks = delayTicks;
        }

        void enqueue(GameSnapshot snapshot, long sentAtTick) {
            pending.add(snapshot);
            queue.add(new long[]{sentAtTick + delayTicks, pending.size() - 1L});
        }

        List<GameSnapshot> drain(long currentTick) {
            List<GameSnapshot> ready = new ArrayList<>();
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
}
