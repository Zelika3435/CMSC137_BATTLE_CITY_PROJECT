package com.battlecity.net.protocol;

/**
 * Server lifecycle phase as encoded on the wire.
 *
 * <p>Mirrors {@code com.battlecity.net.server.ServerPhase} but lives in the protocol package so
 * that client-side code and tests have no dependency on server internals.
 *
 * <p>Wire ordinal is stable: do not reorder values.
 */
public enum LobbyPhase {
    /** Server is open; clients may join; no simulation world exists yet. */
    LOBBY(0),
    /** Quorum reached; brief delay before the match starts; still no simulation. */
    COUNTDOWN(1),
    /** Authoritative simulation is active; match in progress. */
    RUNNING(2),
    /** Match is over; simulation frozen. */
    END(3);

    private final int id;

    LobbyPhase(int id) {
        this.id = id;
    }

    /** Stable wire ordinal. */
    public int id() {
        return id;
    }

    public static LobbyPhase fromId(int id) {
        for (LobbyPhase p : values()) {
            if (p.id == id) return p;
        }
        throw new IllegalArgumentException("unknown LobbyPhase id: " + id);
    }
}
