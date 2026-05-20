package com.battlecity.net.protocol;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.battlecity.game.Direction;
import com.battlecity.game.GameCommand;
import com.battlecity.net.protocol.NetMessages.NetPacket;
import com.battlecity.net.protocol.PacketValidator.ValidationResult;
import org.junit.jupiter.api.Test;

final class PacketValidatorTest {
    @Test
    void rejectsShortPacket() {
        ValidationResult result = PacketValidator.validateRaw(new byte[] {1, 2, 3});
        assertFalse(result.valid());
    }

    @Test
    void rejectsBadProtocolVersion() {
        PacketHeader header = new PacketHeader(
                (byte) 99,
                MessageType.JOIN,
                1,
                -1,
                1,
                0,
                0L
        );
        NetPacket packet = new NetPacket(header, new NetMessages.JoinPayload("x"));
        ValidationResult result = PacketValidator.validateDecoded(packet);
        assertFalse(result.valid());
    }

    @Test
    void acceptsValidJoin() {
        PacketHeader header = new PacketHeader(
                ProtocolConstants.PROTOCOL_VERSION,
                MessageType.JOIN,
                0,
                -1,
                1,
                0,
                0L
        );
        ValidationResult result = PacketValidator.validateDecoded(
                new NetPacket(header, new NetMessages.JoinPayload("Player")));
        assertTrue(result.valid());
    }

    @Test
    void rejectsInputWithoutMoveDir() {
        PacketHeader header = new PacketHeader(
                ProtocolConstants.PROTOCOL_VERSION,
                MessageType.INPUT,
                1,
                0,
                1,
                0,
                0L
        );
        ValidationResult result = PacketValidator.validateDecoded(new NetPacket(
                header,
                new NetMessages.InputPayload(1L, GameCommand.MOVE_DIR, null)));
        assertFalse(result.valid());
    }
}
