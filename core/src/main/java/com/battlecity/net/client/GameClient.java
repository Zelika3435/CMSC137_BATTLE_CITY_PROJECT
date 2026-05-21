package com.battlecity.net.client;

import com.battlecity.game.Direction;
import com.battlecity.game.GameCommand;
import com.battlecity.game.snapshot.GameSnapshot;
import com.battlecity.game.snapshot.NetStatsSnapshot;
import com.battlecity.net.protocol.LobbyPhase;
import com.battlecity.net.protocol.MessageCodec;
import com.battlecity.net.protocol.MessageType;
import com.battlecity.net.protocol.NetMessages;
import com.battlecity.net.protocol.PacketHeader;
import com.battlecity.net.protocol.PacketValidator;
import com.battlecity.net.protocol.ProtocolConstants;
import com.battlecity.net.transport.UdpTransport;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client-side connection to an authoritative {@link com.battlecity.net.server.GameServer}.
 *
 * <p>Lifecycle phases:
 * <ol>
 *   <li>Created and {@link #connect()} called → JOIN sent; {@link #isConnected()} is {@code false}.
 *   <li>JOIN_ACK received → {@link #isConnected()} becomes {@code true};
 *       {@link #clientPhase()} reflects the phase the server reported.
 *   <li>LOBBY_STATE packets arrive → {@link #lobbySnapshot()} is updated.
 *   <li>First SNAPSHOT received → {@link #clientPhase()} becomes {@link LobbyPhase#RUNNING};
 *       input sending is now active.
 * </ol>
 *
 * <p>INPUT messages are suppressed until {@link LobbyPhase#RUNNING} to prevent the server from
 * receiving stale inputs during LOBBY or COUNTDOWN.
 *
 * <p>All methods are intended to be called from a single thread (the LibGDX render thread).
 */
public final class GameClient implements AutoCloseable {

    private final UdpTransport transport;
    private final InetSocketAddress serverAddress;
    private final String playerName;

    private int sessionId;
    private int playerId = -1;
    private int seq;
    private int lastAck;
    private long lastServerTick;
    private long lastPingSentMs;
    private int pingMs;
    private int packetsSent;
    private int packetsReceived;
    private int snapshotsReceived;
    private boolean connected;

    /** Client's best understanding of the current server phase. */
    private LobbyPhase clientPhase = LobbyPhase.LOBBY;

    /** Latest lobby snapshot received via LOBBY_STATE, or {@code null} before first arrival. */
    private LobbySnapshot lobbySnapshot;

    /**
     * Most recent user-visible error from the server (e.g. "server full"), or {@code null}.
     * Cleared on successful JOIN_ACK.
     */
    private String lastError;

    private GameSnapshot previousSnapshot;
    private GameSnapshot currentSnapshot;

    private final AtomicInteger inputsThisTick = new AtomicInteger(0);

    public GameClient(String host, int port, String playerName) throws IOException {
        this.transport = new UdpTransport();
        this.transport.start();
        this.serverAddress = new InetSocketAddress(host, port);
        this.playerName = playerName;
    }

    public void connect() throws IOException {
        PacketHeader header = new PacketHeader(
                ProtocolConstants.PROTOCOL_VERSION,
                MessageType.JOIN,
                0,
                -1,
                nextSeq(),
                0,
                0L
        );
        send(new NetMessages.NetPacket(header, new NetMessages.JoinPayload(playerName)));
    }

    public void poll() {
        UdpTransport.ReceivedDatagram datagram;
        while ((datagram = transport.poll()) != null) {
            handle(datagram.data());
        }
    }

    /**
     * Sends movement/fire input to the server.
     *
     * <p>Suppressed until {@link #clientPhase()} is {@link LobbyPhase#RUNNING} to avoid sending
     * stale input during LOBBY or COUNTDOWN. Also suppressed when not yet connected.
     */
    public void sendInput(Direction moveDir, boolean fire) {
        if (!connected || clientPhase != LobbyPhase.RUNNING) {
            return;
        }
        if (inputsThisTick.get() >= ProtocolConstants.MAX_INPUTS_PER_TICK_PER_CLIENT) {
            return;
        }
        inputsThisTick.incrementAndGet();

        long tickStamp = Math.max(0L, lastServerTick);
        if (moveDir != null) {
            sendInputCommand(tickStamp, GameCommand.MOVE_DIR, moveDir);
        }
        if (fire) {
            sendInputCommand(tickStamp, GameCommand.FIRE, null);
        }
    }

    /**
     * Sends a ready-status update to the server lobby.
     *
     * <p>No-op if not yet connected.
     */
    public void sendSetReady(boolean ready) {
        if (!connected) {
            return;
        }
        PacketHeader header = buildHeader(MessageType.SET_READY);
        try {
            send(new NetMessages.NetPacket(header, new NetMessages.SetReadyPayload(ready)));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Requests the server to immediately start the match (host-only; server validates authority).
     *
     * <p>No-op if not yet connected.
     */
    public void sendStartMatch() {
        if (!connected) {
            return;
        }
        PacketHeader header = buildHeader(MessageType.START_MATCH);
        try {
            send(new NetMessages.NetPacket(header, new NetMessages.StartMatchPayload()));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Sends a graceful DISCONNECT to the server and marks this client as disconnected.
     *
     * <p>Must be called before {@link #close()} when leaving the lobby or match intentionally so
     * the server can free the player slot immediately rather than waiting for a timeout.
     * No-op if not yet connected.
     */
    public void sendDisconnect(String reason) {
        if (!connected) {
            return;
        }
        PacketHeader header = buildHeader(MessageType.DISCONNECT);
        try {
            send(new NetMessages.NetPacket(header, new NetMessages.DisconnectPayload(reason)));
            connected = false;
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /** Resets the per-tick input counter and sends a periodic PING or retries JOIN if not yet connected. */
    public void endTick() {
        inputsThisTick.set(0);
        long now = System.currentTimeMillis();
        if (now - lastPingSentMs > 1000L) {
            lastPingSentMs = now;
            if (!connected) {
                try {
                    connect();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                return;
            }
            try {
                send(new NetMessages.NetPacket(
                        buildHeader(MessageType.PING),
                        new NetMessages.PingPayload(now)));
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    // ---- Accessors ---------------------------------------------------------------------------

    public boolean isConnected() {
        return connected;
    }

    public int playerId() {
        return playerId;
    }

    /** Current phase as last confirmed by the server. */
    public LobbyPhase clientPhase() {
        return clientPhase;
    }

    /**
     * Latest lobby snapshot received from the server, or {@code null} if no LOBBY_STATE has
     * arrived yet.
     */
    public LobbySnapshot lobbySnapshot() {
        return lobbySnapshot;
    }

    /**
     * Most recent error message from the server (e.g. "server full"), or {@code null}.
     * Useful for user-visible error display in the lobby UI.
     */
    public String lastError() {
        return lastError;
    }

    /**
     * Resets client state to lobby mode after a completed match so the same connection can be
     * reused for the next round without re-joining.
     *
     * <p>Clears both snapshots (so {@link com.battlecity.ui.LobbyScreen} does not immediately
     * re-enter {@link com.battlecity.core.AppPhase#MP_MATCH}) and resets {@code clientPhase} to
     * {@link LobbyPhase#LOBBY} (so {@link #sendInput} stays suppressed until the server signals
     * RUNNING again).  The connection, session ID, player ID, and {@link #lobbySnapshot} are
     * preserved; {@code lobbySnapshot} is replaced by the next LOBBY_STATE broadcast.
     */
    public void resetForLobby() {
        previousSnapshot = null;
        currentSnapshot  = null;
        clientPhase      = LobbyPhase.LOBBY;
    }

    /**
     * Returns {@code true} if the local player is the current lobby host
     * (i.e. has the lowest player ID among connected players, as reported by
     * the most recent {@link LobbySnapshot}).
     */
    public boolean isHost() {
        return lobbySnapshot != null && playerId == lobbySnapshot.hostPlayerId();
    }

    public GameSnapshot previousSnapshot() {
        return previousSnapshot;
    }

    public GameSnapshot currentSnapshot() {
        return currentSnapshot;
    }

    public NetStatsSnapshot netStats() {
        int tanks = currentSnapshot == null ? 0 : currentSnapshot.tanks().size();
        int projectiles = currentSnapshot == null ? 0 : currentSnapshot.projectiles().size();
        float loss = packetsSent == 0 ? 0f
                : Math.max(0f, (packetsSent - packetsReceived) * 100f / packetsSent);
        return new NetStatsSnapshot(
                lastServerTick,
                SimulationConstants.FIXED_DT,
                pingMs,
                loss,
                tanks,
                projectiles,
                tanks + projectiles
        );
    }

    // ---- Internal message handling ----------------------------------------------------------

    private void handle(byte[] data) {
        PacketValidator.ValidationResult validation = PacketValidator.validateRaw(data);
        if (!validation.valid()) {
            return;
        }
        NetMessages.NetPacket packet = MessageCodec.decode(data);
        packetsReceived++;
        switch (packet.header().messageType()) {
            case JOIN_ACK    -> handleJoinAck(packet);
            case LOBBY_STATE -> handleLobbyState(packet);
            case SNAPSHOT    -> handleSnapshot(packet);
            case PONG        -> handlePong(packet);
            case ERROR       -> handleError(packet);
            default          -> {}
        }
    }

    private void handleJoinAck(NetMessages.NetPacket packet) {
        NetMessages.JoinAckPayload ack = (NetMessages.JoinAckPayload) packet.payload();
        sessionId  = ack.sessionId();
        playerId   = ack.assignedPlayerId();
        lastServerTick = ack.serverTick();
        clientPhase = ack.currentPhase() != null ? ack.currentPhase() : LobbyPhase.LOBBY;
        connected  = true;
        lastError  = null;
    }

    private void handleLobbyState(NetMessages.NetPacket packet) {
        NetMessages.LobbyStatePayload payload =
                (NetMessages.LobbyStatePayload) packet.payload();
        lastServerTick = packet.header().serverTick();
        clientPhase = payload.phase();
        lobbySnapshot = new LobbySnapshot(
                payload.phase(),
                List.copyOf(payload.players()),
                payload.hostPlayerId(),
                payload.countdownTicksLeft()
        );
    }

    private void handleSnapshot(NetMessages.NetPacket packet) {
        NetMessages.SnapshotPayload payload = (NetMessages.SnapshotPayload) packet.payload();
        GameSnapshot raw = payload.snapshot();
        GameSnapshot snapshot = new GameSnapshot(
                packet.header().serverTick(),
                raw.stateHash(),
                raw.mapWidthTiles(),
                raw.mapHeightTiles(),
                raw.tileSize(),
                raw.tiles(),
                raw.tanks(),
                raw.projectiles(),
                raw.baseDestroyed(),
                raw.matchOver()
        );
        // Warm-up: on the very first snapshot previousSnapshot is set to the same reference so
        // the renderer interpolates between identical positions (no pop / jump at tick 0).
        previousSnapshot = currentSnapshot == null ? snapshot : currentSnapshot;
        currentSnapshot  = snapshot;
        lastServerTick   = snapshot.serverTick();
        lastAck          = packet.header().seq();
        // First snapshot signals RUNNING — input is now unblocked (sendInput checks this phase).
        clientPhase      = LobbyPhase.RUNNING;
        snapshotsReceived++;
    }

    private void handlePong(NetMessages.NetPacket packet) {
        NetMessages.PongPayload pong = (NetMessages.PongPayload) packet.payload();
        pingMs = (int) (System.currentTimeMillis() - pong.clientTimeMs());
    }

    /**
     * On ERROR the server closes this client's slot. We surface the message for display and
     * mark the client disconnected so {@link #endTick()} will retry JOIN (useful if the error
     * was transient; the user may also press ESC to abort).
     */
    private void handleError(NetMessages.NetPacket packet) {
        NetMessages.ErrorPayload err = (NetMessages.ErrorPayload) packet.payload();
        lastError = err.message() != null && !err.message().isEmpty()
                ? err.message()
                : "Server error (code " + err.code() + ")";
        // Lobby operational errors (wrong phase, not host) must not drop an active session.
        if (err.code() != 3 && err.code() != 4) {
            connected = false;
        }
    }

    // ---- Packet helpers ---------------------------------------------------------------------

    private void sendInputCommand(long tickStamp, GameCommand command, Direction moveDir) {
        PacketHeader header = buildHeader(MessageType.INPUT);
        NetMessages.InputPayload payload = new NetMessages.InputPayload(tickStamp, command, moveDir);
        try {
            send(new NetMessages.NetPacket(header, payload));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private PacketHeader buildHeader(MessageType type) {
        return new PacketHeader(
                ProtocolConstants.PROTOCOL_VERSION,
                type,
                sessionId,
                playerId,
                nextSeq(),
                lastAck,
                lastServerTick
        );
    }

    private void send(NetMessages.NetPacket packet) throws IOException {
        packetsSent++;
        transport.send(MessageCodec.encode(packet), serverAddress);
    }

    private int nextSeq() {
        return ++seq;
    }

    @Override
    public void close() {
        transport.close();
    }

    public static final class SimulationConstants {
        public static final float FIXED_DT = 1f / 60f;

        private SimulationConstants() {}
    }
}
