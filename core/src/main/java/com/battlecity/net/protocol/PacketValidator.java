package com.battlecity.net.protocol;

import com.battlecity.game.Direction;
import com.battlecity.game.GameCommand;
import com.battlecity.game.MapFactory;

public final class PacketValidator {
    private PacketValidator() {}

    public static ValidationResult validateRaw(byte[] data) {
        if (data == null || data.length < ProtocolConstants.HEADER_BYTES) {
            return ValidationResult.reject("packet too short");
        }
        if (data.length > ProtocolConstants.MAX_PACKET_BYTES) {
            return ValidationResult.reject("packet too large");
        }
        try {
            NetMessages.NetPacket packet = MessageCodec.decode(data);
            return validateDecoded(packet);
        } catch (RuntimeException ex) {
            return ValidationResult.reject(ex.getMessage());
        }
    }

    public static ValidationResult validateDecoded(NetMessages.NetPacket packet) {
        PacketHeader header = packet.header();
        if (header.protocolVersion() != ProtocolConstants.PROTOCOL_VERSION) {
            return ValidationResult.reject("bad protocol version");
        }
        if (header.playerId() < -1 || header.playerId() >= ProtocolConstants.MAX_PLAYERS) {
            return ValidationResult.reject("bad player id");
        }
        if (header.seq() < 0 || header.ack() < 0) {
            return ValidationResult.reject("negative seq/ack");
        }

        return switch (header.messageType()) {
            case JOIN -> validateJoin((NetMessages.JoinPayload) packet.payload());
            case JOIN_ACK -> validateJoinAck((NetMessages.JoinAckPayload) packet.payload());
            case INPUT -> validateInput((NetMessages.InputPayload) packet.payload());
            case SNAPSHOT -> validateSnapshot((NetMessages.SnapshotPayload) packet.payload());
            case PING, PONG, DISCONNECT, ERROR -> ValidationResult.ok();
        };
    }

    private static ValidationResult validateJoin(NetMessages.JoinPayload payload) {
        if (payload.playerName() == null || payload.playerName().isBlank()
                || payload.playerName().length() > 32) {
            return ValidationResult.reject("invalid player name");
        }
        return ValidationResult.ok();
    }

    private static ValidationResult validateJoinAck(NetMessages.JoinAckPayload payload) {
        if (payload.assignedPlayerId() < 0 || payload.assignedPlayerId() >= ProtocolConstants.MAX_PLAYERS) {
            return ValidationResult.reject("invalid assigned player id");
        }
        return ValidationResult.ok();
    }

    private static ValidationResult validateInput(NetMessages.InputPayload payload) {
        if (payload.tickStamp() < 0) {
            return ValidationResult.reject("negative tick stamp");
        }
        if (payload.command() == GameCommand.MOVE_DIR) {
            if (payload.moveDir() == null) {
                return ValidationResult.reject("move dir required");
            }
            try {
                Direction.fromOrdinal(payload.moveDir().ordinal());
            } catch (RuntimeException ex) {
                return ValidationResult.reject("invalid move dir");
            }
        }
        return ValidationResult.ok();
    }

    private static ValidationResult validateSnapshot(NetMessages.SnapshotPayload payload) {
        var snapshot = payload.snapshot();
        int expectedTiles = MapFactory.DEFAULT_WIDTH * MapFactory.DEFAULT_HEIGHT;
        if (snapshot.tiles().length != expectedTiles) {
            return ValidationResult.reject("invalid tile count");
        }
        if (snapshot.tanks().size() > ProtocolConstants.MAX_PLAYERS) {
            return ValidationResult.reject("too many tanks");
        }
        return ValidationResult.ok();
    }

    public record ValidationResult(boolean valid, String reason) {
        public static ValidationResult ok() {
            return new ValidationResult(true, "");
        }

        public static ValidationResult reject(String reason) {
            return new ValidationResult(false, reason);
        }
    }
}
