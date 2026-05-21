package com.battlecity.net.server;

import com.battlecity.game.QueuedCommand;
import com.battlecity.game.Simulation;
import com.battlecity.game.World;
import com.battlecity.game.Tile;
import com.battlecity.game.snapshot.GameSnapshot;
import com.battlecity.game.snapshot.SnapshotBuilder;
import com.battlecity.game.snapshot.SnapshotTileDelta;
import com.battlecity.game.snapshot.TileChange;
import com.battlecity.net.protocol.SnapshotFormat;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Authoritative multiplayer server.
 *
 * <h3>Phase lifecycle</h3>
 * <pre>  LOBBY → COUNTDOWN → RUNNING → END → LOBBY → …</pre>
 * <ul>
 *   <li><b>LOBBY</b>     — server starts here; accepts JOIN / SET_READY / START_MATCH; no world;
 *       broadcasts {@link MessageType#LOBBY_STATE} at ~{@value #LOBBY_BROADCAST_HZ} Hz.
 *       Transitions to COUNTDOWN when all connected players (≥{@value #MIN_PLAYERS_TO_START})
 *       are ready, <em>or</em> when the host sends {@link MessageType#START_MATCH}.
 *   <li><b>COUNTDOWN</b> — counts down {@value #COUNTDOWN_TICKS} ticks (~3 s); still no
 *       simulation; continues broadcasting LOBBY_STATE.  Rolls back to LOBBY if player count
 *       drops below {@value #MIN_PLAYERS_TO_START}.  Host can skip with START_MATCH.
 *   <li><b>RUNNING</b>   — fresh {@link World#createDefault()} at tick 0; simulation ticks;
 *       {@link MessageType#SNAPSHOT} broadcast every tick.
 *   <li><b>END</b>       — {@code world.matchOver} detected; ticking stops; broadcasts
 *       LOBBY_STATE (phase=END) for {@value #END_PHASE_TICKS} ticks (~5 s), then automatically
 *       resets to LOBBY with ready flags cleared.
 * </ul>
 *
 * <h3>Slot management</h3>
 * Player IDs (0–{@code MAX_PLAYERS-1}) are recycled: a disconnected player's slot is returned
 * to the available pool and can be reassigned to the next joiner.  The <em>host</em> is always
 * the connected player with the lowest assigned ID.
 *
 * <h3>Determinism</h3>
 * {@link #clientsByPlayerId} is a {@link TreeMap} so iterating its values always visits players
 * in ascending ID order.  The RUNNING tick drains commands via this sorted view, ensuring
 * consistent command ordering before {@link Simulation#applyCommands} sorts them further.
 */
public final class GameServer implements AutoCloseable {

    // ---- Constants --------------------------------------------------------------------------

    /** Minimum connected players required to enter {@link ServerPhase#COUNTDOWN}. */
    private static final int MIN_PLAYERS_TO_START = 2;

    /** Ticks to wait in {@link ServerPhase#COUNTDOWN} before starting the match (~3 s at 60 Hz). */
    private static final int COUNTDOWN_TICKS = 3 * 60;

    /** Ticks to stay in {@link ServerPhase#END} before automatically returning to LOBBY (~5 s). */
    private static final int END_PHASE_TICKS = 5 * 60;

    /**
     * Target frequency (Hz) for periodic LOBBY_STATE broadcasts.  At 60 Hz tick rate,
     * {@code LOBBY_BROADCAST_INTERVAL = 60 / LOBBY_BROADCAST_HZ} ticks per send.
     */
    private static final int LOBBY_BROADCAST_HZ = 20;
    private static final int LOBBY_BROADCAST_INTERVAL = 60 / LOBBY_BROADCAST_HZ; // = 3 ticks

    // ---- Network state ----------------------------------------------------------------------

    private final UdpTransport transport;

    /**
     * Keyed by remote address for inbound-packet dispatch. Uses HashMap since address lookups
     * do not require ordering.
     */
    private final Map<InetSocketAddress, ClientConnection> clientsByAddress = new HashMap<>();

    /**
     * Keyed by playerId. <strong>TreeMap</strong> so that {@code values()} always iterates in
     * ascending player-ID order — required for deterministic command collection in RUNNING.
     */
    private final TreeMap<Integer, ClientConnection> clientsByPlayerId = new TreeMap<>();

    /**
     * Recycled pool of available player-ID slots (0 … MAX_PLAYERS-1).  TreeSet gives
     * {@code first()} as the lowest free ID, matching the "host = lowest ID" invariant.
     */
    private final TreeSet<Integer> availableSlots = new TreeSet<>();

    private final AtomicInteger nextSessionId = new AtomicInteger(1);
    private boolean running = true;
    private int globalSeq = 0;

    // ---- Phase state ------------------------------------------------------------------------

    private ServerPhase phase = ServerPhase.LOBBY;

    /**
     * Null in {@link ServerPhase#LOBBY} and {@link ServerPhase#COUNTDOWN}; created fresh on each
     * transition to {@link ServerPhase#RUNNING}.
     */
    private Simulation simulation = null;

    /** Remaining ticks in COUNTDOWN; only meaningful while {@code phase == COUNTDOWN}. */
    private int countdownTicks = 0;

    /** Remaining ticks in END before the server auto-resets to LOBBY. */
    private int endTicks = 0;

    /**
     * Total {@link #updateTick()} calls since server start.  Used to rate-limit periodic
     * LOBBY_STATE broadcasts without relying on the simulation tick counter.
     */
    private long totalTicks = 0L;

    /**
     * Authoritative tiles last sent to clients; used to compute per-tick deltas. Reset when a
     * match enters {@link ServerPhase#RUNNING}.
     */
    private Tile[] lastBroadcastTiles;

    /** When {@code true}, the next {@link #broadcastSnapshot()} sends {@link SnapshotFormat#FULL_MAP}. */
    private boolean nextSnapshotIsFullMap;

    // -----------------------------------------------------------------------------------------

    public GameServer(int port) throws IOException {
        // Populate the slot pool: IDs 0 … MAX_PLAYERS-1.
        for (int i = 0; i < ProtocolConstants.MAX_PLAYERS; i++) {
            availableSlots.add(i);
        }
        // Simulation created lazily in transitionTo(RUNNING).
        this.transport = new UdpTransport(port);
        this.transport.start();
    }

    // ---- Public API -------------------------------------------------------------------------

    /**
     * Returns the active {@link Simulation}, or {@code null} when the phase is
     * {@link ServerPhase#LOBBY}, {@link ServerPhase#COUNTDOWN}, or
     * {@link ServerPhase#END} before a simulation was created.
     */
    public Simulation simulation() {
        return simulation;
    }

    /** The current lifecycle phase. */
    public ServerPhase phase() {
        return phase;
    }

    /** Drains and dispatches all inbound UDP datagrams. Call every iteration regardless of phase. */
    public void pollNetwork() {
        UdpTransport.ReceivedDatagram datagram;
        while ((datagram = transport.poll()) != null) {
            handlePacket(datagram);
        }
    }

    /**
     * Advances the server by one fixed tick.
     *
     * <ul>
     *   <li>{@link ServerPhase#LOBBY}     — periodic LOBBY_STATE broadcast; no auto-transition
     *       (only SET_READY / START_MATCH events trigger COUNTDOWN).
     *   <li>{@link ServerPhase#COUNTDOWN} — periodic LOBBY_STATE broadcast; decrements counter;
     *       transitions to RUNNING at zero.
     *   <li>{@link ServerPhase#RUNNING}   — applies queued inputs in player-ID order, steps the
     *       simulation, broadcasts a snapshot; transitions to END when the match is over.
     *   <li>{@link ServerPhase#END}       — broadcasts LOBBY_STATE (phase=END) at
     *       ~{@value #LOBBY_BROADCAST_HZ} Hz; counts down {@value #END_PHASE_TICKS} ticks then
     *       auto-transitions back to LOBBY with ready flags cleared.
     * </ul>
     */
    public void updateTick() {
        totalTicks++;
        switch (phase) {
            case LOBBY -> {
                if (totalTicks % LOBBY_BROADCAST_INTERVAL == 0) {
                    broadcastLobbyState();
                }
            }
            case COUNTDOWN -> {
                if (totalTicks % LOBBY_BROADCAST_INTERVAL == 0) {
                    broadcastLobbyState();
                }
                if (--countdownTicks <= 0) {
                    transitionTo(ServerPhase.RUNNING);
                }
            }
            case RUNNING -> {
                long tick = simulation.tickCount();
                List<QueuedCommand> commands = new ArrayList<>();
                // Drain in ascending playerId order (clientsByPlayerId is a TreeMap).
                for (ClientConnection client : clientsByPlayerId.values()) {
                    commands.addAll(client.drainInputsForTick(tick));
                }
                simulation.applyCommands(commands);
                simulation.updateTick();
                broadcastSnapshot();
                if (simulation.world().matchOver) {
                    transitionTo(ServerPhase.END);
                }
            }
            case END -> {
                if (totalTicks % LOBBY_BROADCAST_INTERVAL == 0) {
                    broadcastLobbyState();
                }
                if (--endTicks <= 0) {
                    transitionTo(ServerPhase.LOBBY);
                }
            }
        }
    }

    public int connectedPlayers() {
        return clientsByAddress.size();
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public void close() {
        running = false;
        transport.close();
    }

    // ---- Phase transitions ------------------------------------------------------------------

    /**
     * Transitions to {@code next}.  Guards against no-op same-phase transitions to prevent
     * spurious log messages (e.g. re-entering LOBBY after a disconnect while already in LOBBY).
     */
    private void transitionTo(ServerPhase next) {
        if (phase == next) return;
        final ServerPhase prev = phase;
        System.out.printf("[Server] Phase: %s → %s%n", phase, next);
        phase = next;
        switch (next) {
            case COUNTDOWN -> {
                countdownTicks = COUNTDOWN_TICKS;
                System.out.printf("[Server] Match starts in %.1f s  (host=player%d  %d/%d connected)%n",
                        COUNTDOWN_TICKS / 60f, currentHostPlayerId(),
                        clientsByAddress.size(), ProtocolConstants.MAX_PLAYERS);
                broadcastLobbyState();
            }
            case RUNNING -> {
                World world = World.createDefault();
                simulation = new Simulation(world, false, 0L);
                lastBroadcastTiles = null;
                nextSnapshotIsFullMap = true;
                System.out.printf("[Server] Match started — tick=0  players=%d%n",
                        clientsByAddress.size());
            }
            case END -> {
                endTicks = END_PHASE_TICKS;
                System.out.printf("[Server] Match ended at tick=%d  players=%d  endDelay=%.1fs%n",
                        currentTick(), clientsByAddress.size(), END_PHASE_TICKS / 60f);
                broadcastLobbyState();
            }
            case LOBBY -> {
                if (prev == ServerPhase.END) {
                    // Post-match reset: clear ready flags for next match; free simulation memory.
                    // A fresh World is only created when RUNNING begins again.
                    for (ClientConnection c : clientsByPlayerId.values()) {
                        c.ready = false;
                    }
                    simulation = null;
                    System.out.printf("[Server] Match over — lobby reset  (players=%d)%n",
                            clientsByAddress.size());
                } else {
                    // Countdown cancelled (quorum lost or other).
                    System.out.printf("[Server] Returned to lobby  (players=%d)%n",
                            clientsByAddress.size());
                }
                broadcastLobbyState();
            }
        }
    }

    // ---- Lobby state broadcast --------------------------------------------------------------

    /**
     * Sends {@link MessageType#LOBBY_STATE} to every connected client.
     *
     * <p>Players are sorted by ID (guaranteed by {@link #clientsByPlayerId} being a TreeMap).
     * Safe to call in any phase; silently skips broadcast during RUNNING (snapshots are used
     * instead).  Called during LOBBY, COUNTDOWN, and END.
     */
    private void broadcastLobbyState() {
        if (phase == ServerPhase.RUNNING) return;

        List<NetMessages.LobbyPlayerEntry> entries = new ArrayList<>(clientsByPlayerId.size());
        // TreeMap guarantees ascending playerId order.
        for (ClientConnection client : clientsByPlayerId.values()) {
            entries.add(new NetMessages.LobbyPlayerEntry(
                    client.playerId, client.name, client.ready, /* connected */ true));
        }

        NetMessages.LobbyStatePayload payload = new NetMessages.LobbyStatePayload(
                toWirePhase(phase),
                List.copyOf(entries),
                currentHostPlayerId(),
                countdownTicks
        );

        for (ClientConnection client : clientsByAddress.values()) {
            try {
                sendLobbyState(client, payload);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void sendLobbyState(ClientConnection client,
                                NetMessages.LobbyStatePayload payload) throws IOException {
        PacketHeader header = new PacketHeader(
                ProtocolConstants.PROTOCOL_VERSION,
                MessageType.LOBBY_STATE,
                client.sessionId,
                client.playerId,
                globalSeq++,
                client.lastAck,
                currentTick()
        );
        transport.send(
                MessageCodec.encode(new NetMessages.NetPacket(header, payload)),
                client.address
        );
    }

    // ---- Snapshot broadcast (RUNNING only) --------------------------------------------------

    private void broadcastSnapshot() {
        NetMessages.SnapshotPayload payload = buildSnapshotPayload();
        for (ClientConnection client : clientsByPlayerId.values()) {
            try {
                sendSnapshot(client, payload);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private NetMessages.SnapshotPayload buildSnapshotPayload() {
        var world = simulation.world();
        Tile[] currentTiles = world.map.copyTiles();
        GameSnapshot entitySnapshot = SnapshotBuilder.build(world, false);

        if (nextSnapshotIsFullMap || lastBroadcastTiles == null) {
            nextSnapshotIsFullMap = false;
            lastBroadcastTiles = currentTiles.clone();
            GameSnapshot full = new GameSnapshot(
                    entitySnapshot.serverTick(),
                    entitySnapshot.stateHash(),
                    entitySnapshot.mapWidthTiles(),
                    entitySnapshot.mapHeightTiles(),
                    entitySnapshot.tileSize(),
                    lastBroadcastTiles.clone(),
                    entitySnapshot.tanks(),
                    entitySnapshot.projectiles(),
                    entitySnapshot.baseDestroyed(),
                    entitySnapshot.matchOver()
            );
            return NetMessages.SnapshotPayload.fullMap(full);
        }

        List<TileChange> changes = SnapshotTileDelta.collectChanges(lastBroadcastTiles, currentTiles);
        System.arraycopy(currentTiles, 0, lastBroadcastTiles, 0, currentTiles.length);

        NetMessages.SnapshotPayload delta =
                NetMessages.SnapshotPayload.delta(entitySnapshot, changes);
        byte[] encoded = MessageCodec.encode(snapshotPacketForSizeCheck(delta));
        if (encoded.length > ProtocolConstants.MAX_PACKET_BYTES) {
            nextSnapshotIsFullMap = true;
            lastBroadcastTiles = currentTiles.clone();
            GameSnapshot full = new GameSnapshot(
                    entitySnapshot.serverTick(),
                    entitySnapshot.stateHash(),
                    entitySnapshot.mapWidthTiles(),
                    entitySnapshot.mapHeightTiles(),
                    entitySnapshot.tileSize(),
                    lastBroadcastTiles.clone(),
                    entitySnapshot.tanks(),
                    entitySnapshot.projectiles(),
                    entitySnapshot.baseDestroyed(),
                    entitySnapshot.matchOver()
            );
            return NetMessages.SnapshotPayload.fullMap(full);
        }
        return delta;
    }

    private static NetMessages.NetPacket snapshotPacketForSizeCheck(NetMessages.SnapshotPayload payload) {
        PacketHeader header = new PacketHeader(
                ProtocolConstants.PROTOCOL_VERSION,
                MessageType.SNAPSHOT,
                0,
                0,
                0,
                0,
                payload.snapshot().serverTick()
        );
        return new NetMessages.NetPacket(header, payload);
    }

    private void sendSnapshot(ClientConnection client, NetMessages.SnapshotPayload payload)
            throws IOException {
        GameSnapshot snapshot = payload.snapshot();
        PacketHeader header = new PacketHeader(
                ProtocolConstants.PROTOCOL_VERSION,
                MessageType.SNAPSHOT,
                client.sessionId,
                client.playerId,
                globalSeq++,
                client.lastSeq,
                snapshot.serverTick()
        );
        client.packetsSent++;
        transport.send(
                MessageCodec.encode(new NetMessages.NetPacket(header, payload)),
                client.address
        );
    }

    // ---- Packet dispatch --------------------------------------------------------------------

    private void handlePacket(UdpTransport.ReceivedDatagram datagram) {
        PacketValidator.ValidationResult validation = PacketValidator.validateRaw(datagram.data());
        if (!validation.valid()) {
            return;
        }
        NetMessages.NetPacket packet = MessageCodec.decode(datagram.data());
        switch (packet.header().messageType()) {
            case JOIN        -> handleJoin(datagram.source(), packet);
            case INPUT       -> handleInput(datagram.source(), packet);
            case PING        -> handlePing(datagram.source(), packet);
            case SET_READY   -> handleSetReady(datagram.source(), packet);
            case START_MATCH -> handleStartMatch(datagram.source(), packet);
            case CHAT        -> handleChat(datagram.source(), packet);
            case DISCONNECT  -> handleDisconnect(datagram.source());
            default          -> {}
        }
    }

    // ---- JOIN -------------------------------------------------------------------------------

    private void handleJoin(InetSocketAddress address, NetMessages.NetPacket packet) {
        // Reconnect: re-send the ACK so the client can re-enter its current phase.
        if (clientsByAddress.containsKey(address)) {
            sendJoinAck(clientsByAddress.get(address));
            return;
        }

        // Block brand-new joins once the match is underway.
        if (phase == ServerPhase.RUNNING || phase == ServerPhase.END) {
            sendError(address, 2, "match already in progress");
            return;
        }

        if (availableSlots.isEmpty()) {
            sendError(address, 1, "server full");
            return;
        }

        // Claim the lowest available ID so the host is always the smallest active ID.
        int playerId  = availableSlots.first();
        availableSlots.remove(playerId);
        int sessionId = nextSessionId.getAndIncrement();

        NetMessages.JoinPayload join = (NetMessages.JoinPayload) packet.payload();
        ClientConnection client = new ClientConnection(address, playerId, sessionId, join.playerName());
        clientsByAddress.put(address, client);
        clientsByPlayerId.put(playerId, client);

        System.out.printf("[Server] Player %d (%s) joined from %s  (%d/%d  host=player%d)%n",
                playerId, join.playerName(), address,
                clientsByAddress.size(), ProtocolConstants.MAX_PLAYERS,
                currentHostPlayerId());

        sendJoinAck(client);
        broadcastLobbyState();
    }

    private void sendJoinAck(ClientConnection client) {
        long tick = currentTick();
        NetMessages.JoinAckPayload ack = new NetMessages.JoinAckPayload(
                client.playerId, client.sessionId, 0L, tick, toWirePhase(phase));
        PacketHeader header = new PacketHeader(
                ProtocolConstants.PROTOCOL_VERSION,
                MessageType.JOIN_ACK,
                client.sessionId,
                client.playerId,
                globalSeq++,
                0,
                tick
        );
        try {
            transport.send(
                    MessageCodec.encode(new NetMessages.NetPacket(header, ack)),
                    client.address
            );
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    // ---- DISCONNECT -------------------------------------------------------------------------

    /**
     * Removes the client from both maps, recycles its player-ID slot, and updates the lobby.
     * If a COUNTDOWN was in progress and the remaining player count drops below
     * {@value #MIN_PLAYERS_TO_START}, the countdown is cancelled and the server returns to
     * {@link ServerPhase#LOBBY} so remaining players can re-ready.
     */
    private void handleDisconnect(InetSocketAddress address) {
        ClientConnection client = clientsByAddress.remove(address);
        if (client == null) return;

        clientsByPlayerId.remove(client.playerId);
        availableSlots.add(client.playerId);

        System.out.printf("[Server] Player %d (%s) disconnected  (%d/%d remaining  host=player%d)%n",
                client.playerId, client.name,
                clientsByAddress.size(), ProtocolConstants.MAX_PLAYERS,
                currentHostPlayerId());

        if (phase == ServerPhase.LOBBY || phase == ServerPhase.COUNTDOWN) {
            if (phase == ServerPhase.COUNTDOWN
                    && clientsByAddress.size() < MIN_PLAYERS_TO_START) {
                // Not enough players to continue the countdown.
                transitionTo(ServerPhase.LOBBY); // also calls broadcastLobbyState()
            } else {
                broadcastLobbyState();
            }
        }
    }

    // ---- INPUT ------------------------------------------------------------------------------

    private void handleInput(InetSocketAddress address, NetMessages.NetPacket packet) {
        if (phase != ServerPhase.RUNNING) return;
        ClientConnection client = clientsByAddress.get(address);
        if (client == null) return;

        client.lastAck      = packet.header().ack();
        client.packetsAcked = packet.header().ack();

        NetMessages.InputPayload payload = (NetMessages.InputPayload) packet.payload();
        QueuedCommand command = new QueuedCommand(
                client.playerId,
                payload.tickStamp(),
                packet.header().seq(),
                payload.command(),
                payload.moveDir()
        );
        client.registerInput(command, simulation.tickCount());
    }

    // ---- SET_READY --------------------------------------------------------------------------

    /**
     * Toggles a client's ready flag.  After the toggle, checks the auto-start condition:
     * if all connected players (minimum {@value #MIN_PLAYERS_TO_START}) are ready and the server
     * is in {@link ServerPhase#LOBBY}, the countdown begins.
     */
    private void handleSetReady(InetSocketAddress address, NetMessages.NetPacket packet) {
        ClientConnection client = clientsByAddress.get(address);
        if (client == null) return;
        if (phase != ServerPhase.LOBBY && phase != ServerPhase.COUNTDOWN) return;

        client.ready = ((NetMessages.SetReadyPayload) packet.payload()).ready();
        System.out.printf("[Server] Player %d (%s) ready=%b%n",
                client.playerId, client.name, client.ready);

        // Auto-start: if everyone is ready and we have enough players, begin countdown.
        if (phase == ServerPhase.LOBBY && allPlayersReady()) {
            transitionTo(ServerPhase.COUNTDOWN); // also broadcasts lobby state
        } else {
            broadcastLobbyState();
        }
    }

    // ---- START_MATCH ------------------------------------------------------------------------

    /**
     * Allows the <em>host</em> (lowest active player ID) to skip the remaining countdown and
     * force the match to start immediately from {@link ServerPhase#LOBBY} or
     * {@link ServerPhase#COUNTDOWN}.
     */
    private void handleStartMatch(InetSocketAddress address, NetMessages.NetPacket packet) {
        ClientConnection client = clientsByAddress.get(address);
        if (client == null) return;

        if (client.playerId != currentHostPlayerId()) {
            sendError(address, 3, "only the host (player " + currentHostPlayerId()
                    + ") can start the match");
            return;
        }
        if (phase != ServerPhase.LOBBY && phase != ServerPhase.COUNTDOWN) {
            sendError(address, 4, "cannot start match in phase " + phase);
            return;
        }
        System.out.println("[Server] Host requested immediate start");
        transitionTo(ServerPhase.RUNNING);
    }

    // ---- CHAT -------------------------------------------------------------------------------

    private void handleChat(InetSocketAddress address, NetMessages.NetPacket packet) {
        ClientConnection client = clientsByAddress.get(address);
        if (client == null) return;
        
        // Only allow chat in lobby/countdown phases.
        if (phase != ServerPhase.LOBBY && phase != ServerPhase.COUNTDOWN) return;

        NetMessages.ChatPayload chat = (NetMessages.ChatPayload) packet.payload();
        System.out.printf("[Server] Chat from player %d (%s): %s%n",
                client.playerId, client.name, chat.message());

        NetMessages.ChatBroadcastPayload broadcast = new NetMessages.ChatBroadcastPayload(
                client.playerId, client.name, chat.message());

        // Broadcast to all connected clients.
        for (ClientConnection c : clientsByAddress.values()) {
            PacketHeader header = new PacketHeader(
                    ProtocolConstants.PROTOCOL_VERSION,
                    MessageType.CHAT_BROADCAST,
                    c.sessionId,
                    c.playerId,
                    globalSeq++,
                    c.lastAck,
                    currentTick()
            );
            try {
                transport.send(
                        MessageCodec.encode(new NetMessages.NetPacket(header, broadcast)),
                        c.address
                );
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    // ---- PING -------------------------------------------------------------------------------

    private void handlePing(InetSocketAddress address, NetMessages.NetPacket packet) {
        ClientConnection client = clientsByAddress.get(address);
        if (client == null) return;

        client.lastPingMs = System.currentTimeMillis();
        NetMessages.PingPayload ping = (NetMessages.PingPayload) packet.payload();
        PacketHeader header = new PacketHeader(
                ProtocolConstants.PROTOCOL_VERSION,
                MessageType.PONG,
                client.sessionId,
                client.playerId,
                globalSeq++,
                packet.header().ack(),
                currentTick()
        );
        try {
            transport.send(
                    MessageCodec.encode(new NetMessages.NetPacket(header,
                            new NetMessages.PongPayload(ping.clientTimeMs(),
                                    System.currentTimeMillis()))),
                    address
            );
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    // ---- ERROR ------------------------------------------------------------------------------

    private void sendError(InetSocketAddress address, int code, String message) {
        PacketHeader header = new PacketHeader(
                ProtocolConstants.PROTOCOL_VERSION,
                MessageType.ERROR,
                0, -1,
                globalSeq++,
                0,
                currentTick()
        );
        try {
            transport.send(
                    MessageCodec.encode(new NetMessages.NetPacket(header,
                            new NetMessages.ErrorPayload(code, message))),
                    address
            );
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    // ---- Helpers ----------------------------------------------------------------------------

    /** Returns the current authoritative tick, or 0 before the simulation is created. */
    private long currentTick() {
        return simulation != null ? simulation.tickCount() : 0L;
    }

    /**
     * Returns the player ID of the current host: the lowest ID among all connected players,
     * which is naturally the first element of the {@link TreeMap}.  Falls back to 0 if there
     * are no connected players (e.g. during server startup).
     */
    private int currentHostPlayerId() {
        return clientsByPlayerId.isEmpty() ? 0 : clientsByPlayerId.firstKey();
    }

    /**
     * Returns {@code true} if at least {@value #MIN_PLAYERS_TO_START} players are connected
     * and every one of them has their ready flag set.
     */
    private boolean allPlayersReady() {
        if (clientsByPlayerId.size() < MIN_PLAYERS_TO_START) return false;
        for (ClientConnection c : clientsByPlayerId.values()) {
            if (!c.ready) return false;
        }
        return true;
    }

    /** Maps internal {@link ServerPhase} to the wire-safe {@link LobbyPhase}. */
    private static LobbyPhase toWirePhase(ServerPhase p) {
        return switch (p) {
            case LOBBY     -> LobbyPhase.LOBBY;
            case COUNTDOWN -> LobbyPhase.COUNTDOWN;
            case RUNNING   -> LobbyPhase.RUNNING;
            case END       -> LobbyPhase.END;
        };
    }
}
