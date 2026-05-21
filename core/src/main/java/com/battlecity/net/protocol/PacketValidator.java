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
            case JOIN        -> validateJoin((NetMessages.JoinPayload) packet.payload());
            case JOIN_ACK    -> validateJoinAck((NetMessages.JoinAckPayload) packet.payload());
            case INPUT       -> validateInput((NetMessages.InputPayload) packet.payload());
            case SNAPSHOT    -> validateSnapshot((NetMessages.SnapshotPayload) packet.payload());
            case LOBBY_STATE -> validateLobbyState((NetMessages.LobbyStatePayload) packet.payload());
            case SET_READY   -> validateSetReady((NetMessages.SetReadyPayload) packet.payload());
            case CHAT        -> validateChat((NetMessages.ChatPayload) packet.payload());
            case CHAT_BROADCAST -> validateChatBroadcast((NetMessages.ChatBroadcastPayload) packet.payload());
            case START_MATCH -> ValidationResult.ok();
            case PING, PONG, DISCONNECT, ERROR -> ValidationResult.ok();
        };
    }

    // ---- Per-type validators ----------------------------------------------------------------

    private static ValidationResult validateJoin(NetMessages.JoinPayload payload) {
        if (payload.playerName() == null || payload.playerName().isBlank()
                || payload.playerName().length() > 32) {
            return ValidationResult.reject("invalid player name");
        }
        return ValidationResult.ok();
    }

    /**
     * Validates the extended JOIN_ACK including the new {@code currentPhase} field.
     */
    private static ValidationResult validateJoinAck(NetMessages.JoinAckPayload payload) {
        if (payload.assignedPlayerId() < 0
                || payload.assignedPlayerId() >= ProtocolConstants.MAX_PLAYERS) {
            return ValidationResult.reject("invalid assigned player id");
        }
        if (payload.currentPhase() == null) {
            return ValidationResult.reject("null phase in join ack");
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
        int expectedTiles = MapFactory.DEFAULT_WIDTH * MapFactory.DEFAULT_HEIGHT;
        if (payload.snapshot().tiles().length != expectedTiles) {
            return ValidationResult.reject("invalid tile count");
        }
        if (payload.snapshot().tanks().size() > ProtocolConstants.MAX_PLAYERS) {
            return ValidationResult.reject("too many tanks");
        }
        return ValidationResult.ok();
    }

    private static ValidationResult validateLobbyState(NetMessages.LobbyStatePayload payload) {
        if (payload.phase() == null) {
            return ValidationResult.reject("null phase in lobby state");
        }
        if (payload.hostPlayerId() < 0
                || payload.hostPlayerId() >= ProtocolConstants.MAX_PLAYERS) {
            return ValidationResult.reject("invalid hostPlayerId");
        }
        if (payload.countdownTicksLeft() < 0) {
            return ValidationResult.reject("negative countdownTicksLeft");
        }
        if (payload.players() == null
                || payload.players().size() > ProtocolConstants.MAX_PLAYERS) {
            return ValidationResult.reject("invalid player list size");
        }
        for (NetMessages.LobbyPlayerEntry p : payload.players()) {
            if (p.playerId() < 0 || p.playerId() >= ProtocolConstants.MAX_PLAYERS) {
                return ValidationResult.reject("invalid playerId in lobby state");
            }
            if (p.name() == null || p.name().length() > 32) {
                return ValidationResult.reject("invalid player name in lobby state");
            }
        }
        return ValidationResult.ok();
    }

    private static ValidationResult validateSetReady(NetMessages.SetReadyPayload payload) {
        // boolean field; no additional constraints beyond successful decode.
        return ValidationResult.ok();
    }

    private static ValidationResult validateChat(NetMessages.ChatPayload payload) {
        if (payload.message() == null || payload.message().isBlank() || payload.message().length() > 128) {
            return ValidationResult.reject("invalid chat message");
        }
        return ValidationResult.ok();
    }

    private static ValidationResult validateChatBroadcast(NetMessages.ChatBroadcastPayload payload) {
        if (payload.senderPlayerId() < 0 || payload.senderPlayerId() >= ProtocolConstants.MAX_PLAYERS) {
            return ValidationResult.reject("invalid sender id in chat broadcast");
        }
        if (payload.senderName() == null || payload.senderName().isBlank() || payload.senderName().length() > 32) {
            return ValidationResult.reject("invalid sender name in chat broadcast");
        }
        if (payload.message() == null || payload.message().isBlank() || payload.message().length() > 128) {
            return ValidationResult.reject("invalid message in chat broadcast");
        }
        return ValidationResult.ok();
    }

    // ---- Result record ----------------------------------------------------------------------

    public record ValidationResult(boolean valid, String reason) {
        public static ValidationResult ok() {
            return new ValidationResult(true, "");
        }

        public static ValidationResult reject(String reason) {
            return new ValidationResult(false, reason);
        }
    }
}
