package com.battlecity.net.client;

import com.battlecity.game.Direction;
import com.battlecity.game.GameCommand;
import com.battlecity.game.QueuedCommand;
import com.battlecity.game.snapshot.GameSnapshot;
import com.battlecity.game.snapshot.NetStatsSnapshot;
import com.battlecity.net.protocol.MessageCodec;
import com.battlecity.net.protocol.MessageType;
import com.battlecity.net.protocol.NetMessages;
import com.battlecity.net.protocol.PacketHeader;
import com.battlecity.net.protocol.PacketValidator;
import com.battlecity.net.protocol.ProtocolConstants;
import com.battlecity.net.transport.UdpTransport;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

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

    public void sendInput(Direction moveDir, boolean fire) {
        if (!connected) {
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

    public void endTick() {
        inputsThisTick.set(0);
        long now = System.currentTimeMillis();
        if (now - lastPingSentMs > 1000L) {
            lastPingSentMs = now;
            PacketHeader header = new PacketHeader(
                    ProtocolConstants.PROTOCOL_VERSION,
                    MessageType.PING,
                    sessionId,
                    playerId,
                    nextSeq(),
                    lastAck,
                    lastServerTick
            );
            try {
                send(new NetMessages.NetPacket(header, new NetMessages.PingPayload(now)));
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public int playerId() {
        return playerId;
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
        float loss = packetsSent == 0 ? 0f : Math.max(0f, (packetsSent - packetsReceived) * 100f / packetsSent);
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

    private void sendInputCommand(long tickStamp, GameCommand command, Direction moveDir) {
        PacketHeader header = new PacketHeader(
                ProtocolConstants.PROTOCOL_VERSION,
                MessageType.INPUT,
                sessionId,
                playerId,
                nextSeq(),
                lastAck,
                lastServerTick
        );
        NetMessages.InputPayload payload = new NetMessages.InputPayload(tickStamp, command, moveDir);
        try {
            send(new NetMessages.NetPacket(header, payload));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void handle(byte[] data) {
        PacketValidator.ValidationResult validation = PacketValidator.validateRaw(data);
        if (!validation.valid()) {
            return;
        }
        NetMessages.NetPacket packet = MessageCodec.decode(data);
        packetsReceived++;
        switch (packet.header().messageType()) {
            case JOIN_ACK -> handleJoinAck(packet);
            case SNAPSHOT -> handleSnapshot(packet);
            case PONG -> handlePong(packet);
            case ERROR -> connected = false;
            default -> {}
        }
    }

    private void handleJoinAck(NetMessages.NetPacket packet) {
        NetMessages.JoinAckPayload ack = (NetMessages.JoinAckPayload) packet.payload();
        sessionId = ack.sessionId();
        playerId = ack.assignedPlayerId();
        lastServerTick = ack.serverTick();
        connected = true;
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
        previousSnapshot = currentSnapshot == null ? snapshot : currentSnapshot;
        currentSnapshot = snapshot;
        lastServerTick = snapshot.serverTick();
        lastAck = packet.header().seq();
        snapshotsReceived++;
    }

    private void handlePong(NetMessages.NetPacket packet) {
        NetMessages.PongPayload pong = (NetMessages.PongPayload) packet.payload();
        pingMs = (int) (System.currentTimeMillis() - pong.clientTimeMs());
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
