package com.battlecity.net.protocol;

public record PacketHeader(
        byte protocolVersion,
        MessageType messageType,
        int sessionId,
        int playerId,
        int seq,
        int ack,
        long serverTick
) {}
