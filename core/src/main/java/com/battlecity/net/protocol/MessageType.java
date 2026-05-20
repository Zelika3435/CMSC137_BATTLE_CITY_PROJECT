package com.battlecity.net.protocol;

public enum MessageType {
    JOIN(1),
    JOIN_ACK(2),
    INPUT(3),
    SNAPSHOT(4),
    PING(5),
    PONG(6),
    DISCONNECT(7),
    ERROR(8);

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
