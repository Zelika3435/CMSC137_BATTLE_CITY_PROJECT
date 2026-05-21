package com.battlecity.net.protocol;

import com.battlecity.game.Direction;
import com.battlecity.game.GameCommand;
import com.battlecity.game.Tile;
import com.battlecity.game.snapshot.GameSnapshot;
import com.battlecity.game.snapshot.ProjectileSnapshot;
import com.battlecity.game.snapshot.TankSnapshot;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class MessageCodec {
    private MessageCodec() {}

    public static byte[] encode(NetMessages.NetPacket packet) {
        ByteBuffer buffer = ByteBuffer.allocate(ProtocolConstants.MAX_PACKET_BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        writeHeader(buffer, packet.header());
        switch (packet.header().messageType()) {
            case JOIN         -> writeJoin(buffer, (NetMessages.JoinPayload) packet.payload());
            case JOIN_ACK     -> writeJoinAck(buffer, (NetMessages.JoinAckPayload) packet.payload());
            case INPUT        -> writeInput(buffer, (NetMessages.InputPayload) packet.payload());
            case SNAPSHOT     -> writeSnapshot(buffer, (NetMessages.SnapshotPayload) packet.payload());
            case PING         -> writePing(buffer, (NetMessages.PingPayload) packet.payload());
            case PONG         -> writePong(buffer, (NetMessages.PongPayload) packet.payload());
            case DISCONNECT   -> writeDisconnect(buffer, (NetMessages.DisconnectPayload) packet.payload());
            case ERROR        -> writeError(buffer, (NetMessages.ErrorPayload) packet.payload());
            case LOBBY_STATE  -> writeLobbyState(buffer, (NetMessages.LobbyStatePayload) packet.payload());
            case SET_READY    -> writeSetReady(buffer, (NetMessages.SetReadyPayload) packet.payload());
            case START_MATCH  -> writeStartMatch(buffer);
            case CHAT         -> writeChat(buffer, (NetMessages.ChatPayload) packet.payload());
            case CHAT_BROADCAST -> writeChatBroadcast(buffer, (NetMessages.ChatBroadcastPayload) packet.payload());
        }
        buffer.flip();
        byte[] out = new byte[buffer.remaining()];
        buffer.get(out);
        return out;
    }

    public static NetMessages.NetPacket decode(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        PacketHeader header = readHeader(buffer);
        Object payload = switch (header.messageType()) {
            case JOIN        -> readJoin(buffer);
            case JOIN_ACK    -> readJoinAck(buffer);
            case INPUT       -> readInput(buffer);
            case SNAPSHOT    -> readSnapshot(buffer);
            case PING        -> readPing(buffer);
            case PONG        -> readPong(buffer);
            case DISCONNECT  -> readDisconnect(buffer);
            case ERROR       -> readError(buffer);
            case LOBBY_STATE -> readLobbyState(buffer);
            case SET_READY   -> readSetReady(buffer);
            case START_MATCH -> readStartMatch(buffer);
            case CHAT        -> readChat(buffer);
            case CHAT_BROADCAST -> readChatBroadcast(buffer);
        };
        return new NetMessages.NetPacket(header, payload);
    }

    // ---- Header -----------------------------------------------------------------------------

    private static void writeHeader(ByteBuffer buffer, PacketHeader header) {
        buffer.put(header.protocolVersion());
        buffer.put((byte) header.messageType().id());
        buffer.putInt(header.sessionId());
        buffer.put((byte) header.playerId());
        buffer.putInt(header.seq());
        buffer.putInt(header.ack());
        buffer.putLong(header.serverTick());
    }

    private static PacketHeader readHeader(ByteBuffer buffer) {
        byte version    = buffer.get();
        MessageType type = MessageType.fromId(Byte.toUnsignedInt(buffer.get()));
        int sessionId   = buffer.getInt();
        int playerId    = buffer.get();
        int seq         = buffer.getInt();
        int ack         = buffer.getInt();
        long serverTick = buffer.getLong();
        return new PacketHeader(version, type, sessionId, playerId, seq, ack, serverTick);
    }

    // ---- JOIN -------------------------------------------------------------------------------

    private static void writeJoin(ByteBuffer buffer, NetMessages.JoinPayload payload) {
        byte[] nameBytes = payload.playerName().getBytes(StandardCharsets.UTF_8);
        buffer.putShort((short) nameBytes.length);
        buffer.put(nameBytes);
    }

    private static NetMessages.JoinPayload readJoin(ByteBuffer buffer) {
        int len = Short.toUnsignedInt(buffer.getShort());
        byte[] bytes = new byte[len];
        buffer.get(bytes);
        return new NetMessages.JoinPayload(new String(bytes, StandardCharsets.UTF_8));
    }

    // ---- JOIN_ACK (extended with currentPhase) ----------------------------------------------

    /**
     * Wire layout:
     * <pre>
     *   byte  assignedPlayerId
     *   int   sessionId
     *   long  mapSeed
     *   long  serverTick
     *   byte  phaseOrdinal
     * </pre>
     */
    private static void writeJoinAck(ByteBuffer buffer, NetMessages.JoinAckPayload payload) {
        buffer.put((byte) payload.assignedPlayerId());
        buffer.putInt(payload.sessionId());
        buffer.putLong(payload.mapSeed());
        buffer.putLong(payload.serverTick());
        buffer.put((byte) payload.currentPhase().id());
    }

    private static NetMessages.JoinAckPayload readJoinAck(ByteBuffer buffer) {
        int playerId    = Byte.toUnsignedInt(buffer.get());
        int sessionId   = buffer.getInt();
        long mapSeed    = buffer.getLong();
        long serverTick = buffer.getLong();
        LobbyPhase phase = buffer.hasRemaining()
                ? LobbyPhase.fromId(Byte.toUnsignedInt(buffer.get()))
                : LobbyPhase.LOBBY;  // graceful fallback for pre-extension packets
        return new NetMessages.JoinAckPayload(playerId, sessionId, mapSeed, serverTick, phase);
    }

    // ---- INPUT ------------------------------------------------------------------------------

    private static void writeInput(ByteBuffer buffer, NetMessages.InputPayload payload) {
        buffer.putLong(payload.tickStamp());
        buffer.put((byte) payload.command().ordinal());
        buffer.put((byte) (payload.moveDir() == null ? -1 : payload.moveDir().ordinal()));
    }

    private static NetMessages.InputPayload readInput(ByteBuffer buffer) {
        long tickStamp    = buffer.getLong();
        GameCommand command = GameCommand.fromOrdinal(Byte.toUnsignedInt(buffer.get()));
        int dirOrdinal    = Byte.toUnsignedInt(buffer.get());
        Direction dir     = dirOrdinal == 255 ? null : Direction.fromOrdinal(dirOrdinal);
        return new NetMessages.InputPayload(tickStamp, command, dir);
    }

    // ---- SNAPSHOT ---------------------------------------------------------------------------

    private static void writeSnapshot(ByteBuffer buffer, NetMessages.SnapshotPayload payload) {
        GameSnapshot snapshot = payload.snapshot();
        buffer.putLong(snapshot.stateHash());
        buffer.putShort((short) snapshot.mapWidthTiles());
        buffer.putShort((short) snapshot.mapHeightTiles());
        buffer.putFloat(snapshot.tileSize());
        for (Tile tile : snapshot.tiles()) {
            buffer.put((byte) tile.ordinal());
        }
        buffer.put((byte) (snapshot.baseDestroyed() ? 1 : 0));
        buffer.put((byte) (snapshot.matchOver() ? 1 : 0));

        buffer.put((byte) snapshot.tanks().size());
        for (TankSnapshot tank : snapshot.tanks()) {
            buffer.putInt(tank.entityId());
            buffer.put((byte) tank.playerId());
            buffer.putFloat(tank.x());
            buffer.putFloat(tank.y());
            buffer.putFloat(tank.prevX());
            buffer.putFloat(tank.prevY());
            buffer.put((byte) tank.dir().ordinal());
            buffer.put((byte) (tank.alive() ? 1 : 0));
        }

        buffer.put((byte) snapshot.projectiles().size());
        for (ProjectileSnapshot projectile : snapshot.projectiles()) {
            buffer.putInt(projectile.entityId());
            buffer.putFloat(projectile.x());
            buffer.putFloat(projectile.y());
            buffer.putFloat(projectile.prevX());
            buffer.putFloat(projectile.prevY());
            buffer.put((byte) projectile.dir().ordinal());
            buffer.put((byte) (projectile.active() ? 1 : 0));
            buffer.put((byte) projectile.ownerPlayerId());
        }
    }

    private static NetMessages.SnapshotPayload readSnapshot(ByteBuffer buffer) {
        long stateHash = buffer.getLong();
        int width      = Short.toUnsignedInt(buffer.getShort());
        int height     = Short.toUnsignedInt(buffer.getShort());
        float tileSize = buffer.getFloat();
        Tile[] tiles   = new Tile[width * height];
        for (int i = 0; i < tiles.length; i++) {
            tiles[i] = Tile.fromOrdinal(Byte.toUnsignedInt(buffer.get()));
        }
        boolean baseDestroyed = buffer.get() != 0;
        boolean matchOver     = buffer.get() != 0;

        int tankCount = Byte.toUnsignedInt(buffer.get());
        List<TankSnapshot> tanks = new ArrayList<>(tankCount);
        for (int i = 0; i < tankCount; i++) {
            tanks.add(new TankSnapshot(
                    buffer.getInt(),
                    Byte.toUnsignedInt(buffer.get()),
                    buffer.getFloat(),
                    buffer.getFloat(),
                    buffer.getFloat(),
                    buffer.getFloat(),
                    Direction.fromOrdinal(Byte.toUnsignedInt(buffer.get())),
                    buffer.get() != 0
            ));
        }

        int projectileCount = Byte.toUnsignedInt(buffer.get());
        List<ProjectileSnapshot> projectiles = new ArrayList<>(projectileCount);
        for (int i = 0; i < projectileCount; i++) {
            projectiles.add(new ProjectileSnapshot(
                    buffer.getInt(),
                    buffer.getFloat(),
                    buffer.getFloat(),
                    buffer.getFloat(),
                    buffer.getFloat(),
                    Direction.fromOrdinal(Byte.toUnsignedInt(buffer.get())),
                    buffer.get() != 0,
                    Byte.toUnsignedInt(buffer.get())
            ));
        }

        GameSnapshot snapshot = new GameSnapshot(
                0L, stateHash, width, height, tileSize,
                tiles, List.copyOf(tanks), List.copyOf(projectiles),
                baseDestroyed, matchOver);
        return new NetMessages.SnapshotPayload(snapshot);
    }

    // ---- PING / PONG ------------------------------------------------------------------------

    private static void writePing(ByteBuffer buffer, NetMessages.PingPayload payload) {
        buffer.putLong(payload.clientTimeMs());
    }

    private static NetMessages.PingPayload readPing(ByteBuffer buffer) {
        return new NetMessages.PingPayload(buffer.getLong());
    }

    private static void writePong(ByteBuffer buffer, NetMessages.PongPayload payload) {
        buffer.putLong(payload.clientTimeMs());
        buffer.putLong(payload.serverTimeMs());
    }

    private static NetMessages.PongPayload readPong(ByteBuffer buffer) {
        return new NetMessages.PongPayload(buffer.getLong(), buffer.getLong());
    }

    // ---- DISCONNECT / ERROR -----------------------------------------------------------------

    private static void writeDisconnect(ByteBuffer buffer, NetMessages.DisconnectPayload payload) {
        byte[] bytes = payload.reason().getBytes(StandardCharsets.UTF_8);
        buffer.putShort((short) bytes.length);
        buffer.put(bytes);
    }

    private static NetMessages.DisconnectPayload readDisconnect(ByteBuffer buffer) {
        int len = Short.toUnsignedInt(buffer.getShort());
        byte[] bytes = new byte[len];
        buffer.get(bytes);
        return new NetMessages.DisconnectPayload(new String(bytes, StandardCharsets.UTF_8));
    }

    private static void writeError(ByteBuffer buffer, NetMessages.ErrorPayload payload) {
        buffer.putInt(payload.code());
        byte[] bytes = payload.message().getBytes(StandardCharsets.UTF_8);
        buffer.putShort((short) bytes.length);
        buffer.put(bytes);
    }

    private static NetMessages.ErrorPayload readError(ByteBuffer buffer) {
        int code = buffer.getInt();
        int len  = Short.toUnsignedInt(buffer.getShort());
        byte[] bytes = new byte[len];
        buffer.get(bytes);
        return new NetMessages.ErrorPayload(code, new String(bytes, StandardCharsets.UTF_8));
    }

    // ---- LOBBY_STATE ------------------------------------------------------------------------

    /**
     * Wire layout (after header):
     * <pre>
     *   byte  phaseOrdinal
     *   byte  hostPlayerId
     *   int   countdownTicksLeft
     *   byte  playerCount
     *   for each player:
     *     byte  playerId
     *     byte  nameLen (u8)
     *     bytes name    (UTF-8)
     *     byte  flags   (bit 0 = ready, bit 1 = connected)
     * </pre>
     */
    private static void writeLobbyState(ByteBuffer buffer, NetMessages.LobbyStatePayload payload) {
        buffer.put((byte) payload.phase().id());
        buffer.put((byte) payload.hostPlayerId());
        buffer.putInt(payload.countdownTicksLeft());
        buffer.put((byte) payload.players().size());
        for (NetMessages.LobbyPlayerEntry p : payload.players()) {
            buffer.put((byte) p.playerId());
            byte[] nameBytes = p.name().getBytes(StandardCharsets.UTF_8);
            buffer.put((byte) nameBytes.length);
            buffer.put(nameBytes);
            byte flags = (byte) ((p.ready() ? 1 : 0) | (p.connected() ? 2 : 0));
            buffer.put(flags);
        }
    }

    private static NetMessages.LobbyStatePayload readLobbyState(ByteBuffer buffer) {
        LobbyPhase phase         = LobbyPhase.fromId(Byte.toUnsignedInt(buffer.get()));
        int hostPlayerId         = Byte.toUnsignedInt(buffer.get());
        int countdownTicksLeft   = buffer.getInt();
        int count                = Byte.toUnsignedInt(buffer.get());
        List<NetMessages.LobbyPlayerEntry> players = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int playerId      = Byte.toUnsignedInt(buffer.get());
            int nameLen       = Byte.toUnsignedInt(buffer.get());
            byte[] nameBytes  = new byte[nameLen];
            buffer.get(nameBytes);
            String name       = new String(nameBytes, StandardCharsets.UTF_8);
            byte flags        = buffer.get();
            boolean ready     = (flags & 1) != 0;
            boolean connected = (flags & 2) != 0;
            players.add(new NetMessages.LobbyPlayerEntry(playerId, name, ready, connected));
        }
        return new NetMessages.LobbyStatePayload(phase, List.copyOf(players), hostPlayerId, countdownTicksLeft);
    }

    // ---- SET_READY --------------------------------------------------------------------------

    private static void writeSetReady(ByteBuffer buffer, NetMessages.SetReadyPayload payload) {
        buffer.put((byte) (payload.ready() ? 1 : 0));
    }

    private static NetMessages.SetReadyPayload readSetReady(ByteBuffer buffer) {
        return new NetMessages.SetReadyPayload(buffer.get() != 0);
    }

    // ---- START_MATCH ------------------------------------------------------------------------

    /**
     * Writes a single reserved padding byte so the packet length exceeds
     * {@link ProtocolConstants#HEADER_BYTES} (which is checked by {@link PacketValidator}).
     */
    private static void writeStartMatch(ByteBuffer buffer) {
        buffer.put((byte) 0);
    }

    private static NetMessages.StartMatchPayload readStartMatch(ByteBuffer buffer) {
        if (buffer.hasRemaining()) buffer.get(); // consume padding byte
        return new NetMessages.StartMatchPayload();
    }

    // ---- CHAT -------------------------------------------------------------------------------

    private static void writeChat(ByteBuffer buffer, NetMessages.ChatPayload payload) {
        byte[] bytes = payload.message().getBytes(StandardCharsets.UTF_8);
        buffer.putShort((short) bytes.length);
        buffer.put(bytes);
    }

    private static NetMessages.ChatPayload readChat(ByteBuffer buffer) {
        int len = Short.toUnsignedInt(buffer.getShort());
        byte[] bytes = new byte[len];
        buffer.get(bytes);
        return new NetMessages.ChatPayload(new String(bytes, StandardCharsets.UTF_8));
    }

    // ---- CHAT_BROADCAST ---------------------------------------------------------------------

    private static void writeChatBroadcast(ByteBuffer buffer, NetMessages.ChatBroadcastPayload payload) {
        buffer.put((byte) payload.senderPlayerId());
        byte[] nameBytes = payload.senderName().getBytes(StandardCharsets.UTF_8);
        buffer.put((byte) nameBytes.length);
        buffer.put(nameBytes);
        byte[] msgBytes = payload.message().getBytes(StandardCharsets.UTF_8);
        buffer.putShort((short) msgBytes.length);
        buffer.put(msgBytes);
    }

    private static NetMessages.ChatBroadcastPayload readChatBroadcast(ByteBuffer buffer) {
        int senderId = Byte.toUnsignedInt(buffer.get());
        int nameLen = Byte.toUnsignedInt(buffer.get());
        byte[] nameBytes = new byte[nameLen];
        buffer.get(nameBytes);
        int msgLen = Short.toUnsignedInt(buffer.getShort());
        byte[] msgBytes = new byte[msgLen];
        buffer.get(msgBytes);
        return new NetMessages.ChatBroadcastPayload(
                senderId,
                new String(nameBytes, StandardCharsets.UTF_8),
                new String(msgBytes, StandardCharsets.UTF_8)
        );
    }
}
