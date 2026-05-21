package com.battlecity.net.protocol;

public final class ProtocolConstants {
    public static final byte PROTOCOL_VERSION = 1;
    public static final int DEFAULT_PORT = 9000;
    public static final int MAX_PLAYERS = 4;
    public static final int MAX_PACKET_BYTES = 4096;
    public static final int MAX_INPUTS_PER_TICK_PER_CLIENT = 4;
    public static final int HEADER_BYTES = 24;

    /** Tile grid cells in the default arena ({@value com.battlecity.game.MapFactory#DEFAULT_WIDTH}×{@value com.battlecity.game.MapFactory#DEFAULT_HEIGHT}). */
    public static final int DEFAULT_TILE_COUNT =
            com.battlecity.game.MapFactory.DEFAULT_WIDTH * com.battlecity.game.MapFactory.DEFAULT_HEIGHT;

    /**
     * Maximum tile mutations per delta SNAPSHOT (entire grid fits in one UDP datagram).
     */
    public static final int MAX_TILE_CHANGES_PER_SNAPSHOT = DEFAULT_TILE_COUNT;

    /**
     * How many ticks ahead of {@code lastServerTick} the client stamps each input.
     *
     * <p>Clients use {@code tickStamp = lastServerTick + INPUT_LEAD_TICKS} so that by the time
     * the UDP packet traverses a typical LAN (20–80 ms RTT ≈ 1–5 ticks at 60 Hz) the target
     * tick is still in the server's near future or present.  A lead of 2 ticks covers
     * one-way latencies up to ~33 ms without staling the command before it arrives.
     */
    public static final int INPUT_LEAD_TICKS = 2;

    /**
     * Maximum number of ticks into the future that the server will accept an input stamp.
     *
     * <p>Must be ≥ {@link #INPUT_LEAD_TICKS} plus slack for jitter.  Commands stamped beyond
     * this window are rejected as bogus (possible clock skew or cheating).
     */
    public static final int MAX_INPUT_FUTURE_TICKS = 4;

    private ProtocolConstants() {}
}
