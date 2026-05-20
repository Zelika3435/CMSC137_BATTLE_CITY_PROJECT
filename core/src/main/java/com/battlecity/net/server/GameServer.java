package com.battlecity.net.server;

import com.battlecity.game.GameCommand;
import com.battlecity.game.QueuedCommand;
import com.battlecity.game.Simulation;
import com.battlecity.game.World;
import com.battlecity.game.snapshot.GameSnapshot;
import com.battlecity.game.snapshot.SnapshotBuilder;
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
import java.util.concurrent.atomic.AtomicInteger;

public final class GameServer implements AutoCloseable {
    private final UdpTransport transport;
    private final Simulation simulation;
    private final Map<InetSocketAddress, ClientConnection> clientsByAddress = new HashMap<>();
    private final Map<Integer, ClientConnection> clientsByPlayerId = new HashMap<>();
    private final AtomicInteger nextSessionId = new AtomicInteger(1);
    private int nextPlayerId = 0;
    private boolean running = true;
    private int globalSeq = 0;

    public GameServer(int port) throws IOException {
        World world = World.createDefault();
        this.simulation = new Simulation(world, false, 0L);
        this.transport = new UdpTransport(port);
        this.transport.start();
    }

    public Simulation simulation() {
        return simulation;
    }

    public void pollNetwork() {
        UdpTransport.ReceivedDatagram datagram;
        while ((datagram = transport.poll()) != null) {
            handlePacket(datagram);
        }
    }

    public void updateTick() {
        long tick = simulation.tickCount();
        List<QueuedCommand> commands = new ArrayList<>();
        for (ClientConnection client : clientsByAddress.values()) {
            commands.addAll(client.drainInputsForTick(tick));
        }
        simulation.applyCommands(commands);
        simulation.updateTick();
        broadcastSnapshot();
    }

    private void broadcastSnapshot() {
        GameSnapshot snapshot = simulation.snapshot();
        for (ClientConnection client : clientsByAddress.values()) {
            try {
                sendSnapshot(client, snapshot);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void sendSnapshot(ClientConnection client, GameSnapshot snapshot) throws IOException {
        PacketHeader header = new PacketHeader(
                ProtocolConstants.PROTOCOL_VERSION,
                MessageType.SNAPSHOT,
                client.sessionId,
                client.playerId,
                globalSeq++,
                client.lastAck,
                snapshot.serverTick()
        );
        NetMessages.NetPacket packet = new NetMessages.NetPacket(
                header,
                new NetMessages.SnapshotPayload(snapshot)
        );
        client.packetsSent++;
        transport.send(MessageCodec.encode(packet), client.address);
    }

    private void handlePacket(UdpTransport.ReceivedDatagram datagram) {
        PacketValidator.ValidationResult validation = PacketValidator.validateRaw(datagram.data());
        if (!validation.valid()) {
            return;
        }

        NetMessages.NetPacket packet = MessageCodec.decode(datagram.data());
        switch (packet.header().messageType()) {
            case JOIN -> handleJoin(datagram.source(), packet);
            case INPUT -> handleInput(datagram.source(), packet);
            case PING -> handlePing(datagram.source(), packet);
            case DISCONNECT -> clientsByAddress.remove(datagram.source());
            default -> {}
        }
    }

    private void handleJoin(InetSocketAddress address, NetMessages.NetPacket packet) {
        if (clientsByAddress.containsKey(address)) {
            return;
        }
        if (nextPlayerId >= ProtocolConstants.MAX_PLAYERS) {
            sendError(address, 1, "server full");
            return;
        }

        int playerId = nextPlayerId++;
        int sessionId = nextSessionId.getAndIncrement();
        ClientConnection client = new ClientConnection(address, playerId, sessionId);
        clientsByAddress.put(address, client);
        clientsByPlayerId.put(playerId, client);

        NetMessages.JoinAckPayload ack = new NetMessages.JoinAckPayload(
                playerId,
                sessionId,
                0L,
                simulation.tickCount()
        );
        PacketHeader header = new PacketHeader(
                ProtocolConstants.PROTOCOL_VERSION,
                MessageType.JOIN_ACK,
                sessionId,
                playerId,
                globalSeq++,
                0,
                simulation.tickCount()
        );
        try {
            transport.send(
                    MessageCodec.encode(new NetMessages.NetPacket(header, ack)),
                    address
            );
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void handleInput(InetSocketAddress address, NetMessages.NetPacket packet) {
        ClientConnection client = clientsByAddress.get(address);
        if (client == null) {
            return;
        }
        client.lastAck = packet.header().ack();
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

    private void handlePing(InetSocketAddress address, NetMessages.NetPacket packet) {
        ClientConnection client = clientsByAddress.get(address);
        if (client == null) {
            return;
        }
        client.lastPingMs = System.currentTimeMillis();
        NetMessages.PingPayload ping = (NetMessages.PingPayload) packet.payload();
        PacketHeader header = new PacketHeader(
                ProtocolConstants.PROTOCOL_VERSION,
                MessageType.PONG,
                client.sessionId,
                client.playerId,
                globalSeq++,
                packet.header().ack(),
                simulation.tickCount()
        );
        NetMessages.PongPayload pong = new NetMessages.PongPayload(
                ping.clientTimeMs(),
                System.currentTimeMillis()
        );
        try {
            transport.send(MessageCodec.encode(new NetMessages.NetPacket(header, pong)), address);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void sendError(InetSocketAddress address, int code, String message) {
        PacketHeader header = new PacketHeader(
                ProtocolConstants.PROTOCOL_VERSION,
                MessageType.ERROR,
                0,
                -1,
                globalSeq++,
                0,
                simulation.tickCount()
        );
        try {
            transport.send(
                    MessageCodec.encode(new NetMessages.NetPacket(header, new NetMessages.ErrorPayload(code, message))),
                    address
            );
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public int connectedPlayers() {
        return clientsByAddress.size();
    }

    @Override
    public void close() {
        running = false;
        transport.close();
    }

    public boolean isRunning() {
        return running;
    }
}
