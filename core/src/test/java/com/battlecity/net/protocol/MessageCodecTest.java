package com.battlecity.net.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.battlecity.game.Direction;
import com.battlecity.game.GameCommand;
import com.battlecity.game.Simulation;
import com.battlecity.game.World;
import com.battlecity.game.snapshot.GameSnapshot;
import com.battlecity.net.protocol.NetMessages.NetPacket;
import org.junit.jupiter.api.Test;

final class MessageCodecTest {
    @Test
    void roundtrip_join() {
        PacketHeader header = header(MessageType.JOIN);
        NetPacket original = new NetPacket(header, new NetMessages.JoinPayload("Tank1"));
        NetPacket decoded = MessageCodec.decode(MessageCodec.encode(original));
        assertEquals(MessageType.JOIN, decoded.header().messageType());
        assertEquals("Tank1", ((NetMessages.JoinPayload) decoded.payload()).playerName());
    }

    @Test
    void roundtrip_input() {
        PacketHeader header = header(MessageType.INPUT);
        NetPacket original = new NetPacket(
                header,
                new NetMessages.InputPayload(42L, GameCommand.MOVE_DIR, Direction.UP));
        NetPacket decoded = MessageCodec.decode(MessageCodec.encode(original));
        NetMessages.InputPayload payload = (NetMessages.InputPayload) decoded.payload();
        assertEquals(42L, payload.tickStamp());
        assertEquals(GameCommand.MOVE_DIR, payload.command());
        assertEquals(Direction.UP, payload.moveDir());
    }

    @Test
    void roundtrip_snapshot() {
        Simulation simulation = new Simulation(World.createDefault(), false, 0L);
        simulation.updateTick();
        GameSnapshot snapshot = simulation.snapshot();

        PacketHeader header = new PacketHeader(
                ProtocolConstants.PROTOCOL_VERSION,
                MessageType.SNAPSHOT,
                7,
                2,
                9,
                8,
                snapshot.serverTick()
        );
        NetPacket original = new NetPacket(header, NetMessages.SnapshotPayload.fullMap(snapshot));
        NetPacket decoded = MessageCodec.decode(MessageCodec.encode(original));
        NetMessages.SnapshotPayload payload = (NetMessages.SnapshotPayload) decoded.payload();
        assertEquals(SnapshotFormat.FULL_MAP, payload.format());
        assertEquals(snapshot.stateHash(), payload.snapshot().stateHash());
        assertEquals(snapshot.tanks().size(), payload.snapshot().tanks().size());
        assertEquals(snapshot.tiles().length, payload.snapshot().tiles().length);
    }

    private static PacketHeader header(MessageType type) {
        return new PacketHeader(
                ProtocolConstants.PROTOCOL_VERSION,
                type,
                1,
                0,
                1,
                0,
                10L
        );
    }
}
