package com.battlecity.net.protocol;

import com.battlecity.game.Direction;
import com.battlecity.game.GameCommand;
import com.battlecity.game.snapshot.GameSnapshot;

public final class NetMessages {
    private NetMessages() {}

    public record JoinPayload(String playerName) {}

    public record JoinAckPayload(
            int assignedPlayerId,
            int sessionId,
            long mapSeed,
            long serverTick
    ) {}

    public record InputPayload(
            long tickStamp,
            GameCommand command,
            Direction moveDir
    ) {}

    public record SnapshotPayload(GameSnapshot snapshot) {}

    public record PingPayload(long clientTimeMs) {}

    public record PongPayload(long clientTimeMs, long serverTimeMs) {}

    public record ErrorPayload(int code, String message) {}

    public record DisconnectPayload(String reason) {}

    public record NetPacket(PacketHeader header, Object payload) {}
}
