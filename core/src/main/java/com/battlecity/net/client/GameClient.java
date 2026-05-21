package com.battlecity.net.client;

import com.battlecity.game.Direction;
import com.battlecity.game.GameCommand;
import com.battlecity.game.LocalPrediction;
import com.battlecity.game.Tile;
import com.battlecity.game.TileMap;
import com.battlecity.game.snapshot.GameSnapshot;
import com.battlecity.game.snapshot.NetStatsSnapshot;
import com.battlecity.game.snapshot.SnapshotTileDelta;
import com.battlecity.game.snapshot.TankSnapshot;
import com.battlecity.net.protocol.SnapshotFormat;
import java.util.Arrays;
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
import java.util.ArrayList;
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

    public record ChatEntry(int playerId, String name, String message) {}

    private final UdpTransport transport;
    private final InetSocketAddress serverAddress;
    private final String playerName;

    private int sessionId;
    private int playerId = -1;
    private int seq;
    private int lastAck;
    private long lastServerTick;
    private long lastPingSentMs;
    private int rttMs;
    private final ClientNetTelemetry telemetry = new ClientNetTelemetry();
    private boolean connected;

    /** Client's best understanding of the current server phase. */
    private LobbyPhase clientPhase = LobbyPhase.LOBBY;

    /** Latest lobby snapshot received via LOBBY_STATE, or {@code null} before first arrival. */
    private LobbySnapshot lobbySnapshot;

    /** Received chat messages, capped to recent entries for UI display. */
    private final List<ChatEntry> chatMessages = new ArrayList<>();

    /**
     * Most recent user-visible error from the server (e.g. "server full"), or {@code null}.
     * Cleared on successful JOIN_ACK.
     */
    private String lastError;

    private final SnapshotInterpolationBuffer snapshotBuffer = new SnapshotInterpolationBuffer();

    /**
     * Client-side prediction for the local player's tank. {@code null} until the first
     * server snapshot arrives (so {@link #playerId} is known).  Reset to {@code null} on
     * {@link #resetForLobby()}.  Active only during {@link LobbyPhase#RUNNING}.
     */
    private LocalPrediction localPrediction;

    /**
     * Tile map kept in sync with incoming snapshots so {@link #localPrediction} can perform
     * collision queries during {@link #stepPrediction}.  {@code null} until first snapshot.
     */
    private TileMap predTileMap;

    /**
     * Local tile buffer merged from {@link SnapshotFormat#FULL_MAP} plus per-tick deltas.
     * {@code null} until the first full-map snapshot of a match.
     */
    private Tile[] clientTiles;

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

    /**
     * Drains inbound UDP datagrams and updates snapshots/lobby state. Safe to call every
     * render frame (LibGDX thread only); does not send inputs or advance simulation.
     */
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

        // Stamp the input a few ticks ahead of the last known server tick so that by the
        // time the UDP packet traverses the LAN the target tick is still in the server's
        // near future (or present).  The server clamps to MAX_INPUT_FUTURE_TICKS so a
        // loopback host (RTT ≈ 0) is unaffected — the lead just means its input queues
        // for tick+2 before being drained, which is within the normal 120-tick window.
        long tickStamp = Math.max(0L, lastServerTick + ProtocolConstants.INPUT_LEAD_TICKS);
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
     * Sends a chat message to the lobby.
     *
     * <p>No-op if not yet connected.
     */
    public void sendChat(String message) {
        if (!connected || message == null || message.isBlank()) {
            return;
        }
        PacketHeader header = buildHeader(MessageType.CHAT);
        try {
            send(new NetMessages.NetPacket(header, new NetMessages.ChatPayload(message)));
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

    /** Read-only list of recent chat messages. */
    public List<ChatEntry> chatMessages() {
        return List.copyOf(chatMessages);
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
        snapshotBuffer.clear();
        telemetry.reset();
        clientPhase      = LobbyPhase.LOBBY;
        localPrediction  = null;
        predTileMap      = null;
        clientTiles      = null;
    }

    /**
     * Advances the local prediction by one fixed timestep using the supplied movement input.
     *
     * <p>Must be called once per client tick from {@link com.battlecity.core.MpMatchPhaseDriver},
     * just after {@link #sendInput} so the same input is applied locally and sent to the server
     * in the same tick.  No-op if the prediction has not been seeded yet (before first snapshot).
     *
     * @param moveDir movement direction this tick, or {@code null} for no movement
     */
    public void stepPrediction(Direction moveDir) {
        if (localPrediction != null && clientPhase == LobbyPhase.RUNNING) {
            localPrediction.applyInput(moveDir);
        }
    }

    /**
     * Returns an immutable snapshot of the current predicted local tank pose, suitable for
     * passing to the renderer as a replacement for the server-interpolated local player position.
     *
     * @return predicted pose snapshot, or {@code null} if the prediction has not been seeded yet
     */
    public TankSnapshot predictedLocalTank() {
        return localPrediction == null ? null : localPrediction.snapshot();
    }

    /**
     * Returns {@code true} if the local player is the current lobby host
     * (i.e. has the lowest player ID among connected players, as reported by
     * the most recent {@link LobbySnapshot}).
     */
    public boolean isHost() {
        return lobbySnapshot != null && playerId == lobbySnapshot.hostPlayerId();
    }

    /** Latest authoritative snapshot (match logic); not delayed by the render buffer. */
    public GameSnapshot currentSnapshot() {
        return snapshotBuffer.latestSnapshot();
    }

    /**
     * Buffered prev/cur/alpha for render interpolation at {@code serverTime - bufferMs}.
     *
     * @return {@code null} before the first snapshot arrives
     */
    public SnapshotInterpolationBuffer.InterpolationSample interpolationSample() {
        return snapshotBuffer.sample();
    }

    public NetStatsSnapshot netStats() {
        long now = System.currentTimeMillis();
        telemetry.refresh(now);
        GameSnapshot latest = snapshotBuffer.latestSnapshot();
        int tanks = latest == null ? 0 : latest.tanks().size();
        int projectiles = latest == null ? 0 : latest.projectiles().size();
        float predErr  = localPrediction == null ? 0f : localPrediction.lastErrorPx();
        boolean predSnap = localPrediction != null && localPrediction.lastReconcileWasSnap();
        return new NetStatsSnapshot(
                lastServerTick,
                SimulationConstants.FIXED_DT,
                rttMs,
                telemetry.snapshotsPerSecond(),
                telemetry.snapshotAgeTicks(now),
                tanks,
                projectiles,
                tanks + projectiles,
                predErr,
                predSnap,
                snapshotBuffer.bufferDelayMs(),
                telemetry.unackedInputs()
        );
    }

    // ---- Internal message handling ----------------------------------------------------------

    private void handle(byte[] data) {
        PacketValidator.ValidationResult validation = PacketValidator.validateRaw(data);
        if (!validation.valid()) {
            return;
        }
        NetMessages.NetPacket packet = MessageCodec.decode(data);
        switch (packet.header().messageType()) {
            case JOIN_ACK    -> handleJoinAck(packet);
            case LOBBY_STATE -> handleLobbyState(packet);
            case SNAPSHOT    -> handleSnapshot(packet);
            case CHAT_BROADCAST -> handleChatBroadcast(packet);
            case PONG        -> handlePong(packet);
            case ERROR       -> handleError(packet);
            default          -> {}
        }
    }

    private void handleChatBroadcast(NetMessages.NetPacket packet) {
        NetMessages.ChatBroadcastPayload payload = (NetMessages.ChatBroadcastPayload) packet.payload();
        chatMessages.add(new ChatEntry(payload.senderPlayerId(), payload.senderName(), payload.message()));
        if (chatMessages.size() > 10) {
            chatMessages.remove(0); // keep only last 10 messages
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
        Tile[] mergedTiles = mergeTiles(payload);
        if (mergedTiles == null) {
            return;
        }
        GameSnapshot snapshot = new GameSnapshot(
                packet.header().serverTick(),
                raw.stateHash(),
                raw.mapWidthTiles(),
                raw.mapHeightTiles(),
                raw.tileSize(),
                mergedTiles,
                raw.tanks(),
                raw.projectiles(),
                raw.baseDestroyed(),
                raw.matchOver()
        );
        snapshotBuffer.push(snapshot);
        lastServerTick   = snapshot.serverTick();
        lastAck          = packet.header().seq();
        telemetry.onSnapshotReceived(snapshot.serverTick(), System.currentTimeMillis());
        telemetry.onInputAck(packet.header().ack());
        // First snapshot signals RUNNING — input is now unblocked (sendInput checks this phase).
        clientPhase      = LobbyPhase.RUNNING;

        reconcilePrediction(snapshot);
    }

    /**
     * Initialises {@link #localPrediction} on the first snapshot and reconciles it on every
     * subsequent one.  The prediction tile map is also refreshed so future collision queries
     * reflect any tile destructions that occurred on the server.
     */
    private void reconcilePrediction(GameSnapshot snapshot) {
        TankSnapshot localTank = findTankByPlayerId(snapshot, playerId);
        if (localTank == null) {
            return; // our player slot not yet present in this snapshot
        }

        // Keep the prediction tile map in sync with the authoritative server map.
        if (predTileMap == null) {
            predTileMap = new TileMap(
                    snapshot.mapWidthTiles(), snapshot.mapHeightTiles(), snapshot.tileSize());
        }
        predTileMap.copyFrom(snapshot.tiles());

        if (localPrediction == null) {
            // First snapshot: seed the prediction from server truth.
            localPrediction = new LocalPrediction(localTank, predTileMap);
        } else {
            // Subsequent snapshots: reconcile predicted pose against server truth.
            localPrediction.updateMap(predTileMap);
            localPrediction.reconcile(localTank);
        }
    }

    private Tile[] mergeTiles(NetMessages.SnapshotPayload payload) {
        if (payload.format() == SnapshotFormat.FULL_MAP) {
            clientTiles = Arrays.copyOf(payload.snapshot().tiles(), payload.snapshot().tiles().length);
            return Arrays.copyOf(clientTiles, clientTiles.length);
        }
        if (clientTiles == null) {
            return null;
        }
        SnapshotTileDelta.applyChanges(clientTiles, payload.tileChanges());
        return Arrays.copyOf(clientTiles, clientTiles.length);
    }

    private static TankSnapshot findTankByPlayerId(GameSnapshot snapshot, int playerId) {
        for (TankSnapshot t : snapshot.tanks()) {
            if (t.playerId() == playerId) {
                return t;
            }
        }
        return null;
    }

    private void handlePong(NetMessages.NetPacket packet) {
        NetMessages.PongPayload pong = (NetMessages.PongPayload) packet.payload();
        rttMs = (int) (System.currentTimeMillis() - pong.clientTimeMs());
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
        telemetry.onInputSent(header.seq());
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
