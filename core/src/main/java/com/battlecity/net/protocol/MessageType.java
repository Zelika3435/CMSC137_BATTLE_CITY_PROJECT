package com.battlecity.net.protocol;

public enum MessageType {
    JOIN(1),
    JOIN_ACK(2),
    INPUT(3),
    SNAPSHOT(4),
    PING(5),
    PONG(6),
    DISCONNECT(7),
    ERROR(8),
    /** S→C: lobby roster snapshot (phase, players, host, countdown). */
    LOBBY_STATE(9),
    /** C→S: client toggles its ready flag in the lobby. */
    SET_READY(10),
    /** C→S: host requests an immediate match start (skips remaining countdown). */
    START_MATCH(11),
    /** C→S: player sends a chat message to the lobby. */
    CHAT(12),
    /** S→C: server broadcasts a chat message to all connected clients. */
    CHAT_BROADCAST(13);

    private final int id;

    MessageType(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static MessageType fromId(int id) {
        for (MessageType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("unknown message type: " + id);
    }
}
