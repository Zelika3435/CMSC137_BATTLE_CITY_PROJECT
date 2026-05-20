package com.battlecity.net.protocol;

public final class ProtocolConstants {
    public static final byte PROTOCOL_VERSION = 1;
    public static final int DEFAULT_PORT = 9000;
    public static final int MAX_PLAYERS = 4;
    public static final int MAX_PACKET_BYTES = 4096;
    public static final int MAX_INPUTS_PER_TICK_PER_CLIENT = 4;
    public static final int HEADER_BYTES = 24;

    private ProtocolConstants() {}
}
