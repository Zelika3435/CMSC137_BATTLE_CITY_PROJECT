package com.battlecity.game;

public record QueuedCommand(
        int playerId,
        long tickStamp,
        int seq,
        GameCommand command,
        Direction moveDir
) {
    public QueuedCommand {
        if (playerId < 0 || playerId >= World.MAX_PLAYERS) {
            throw new IllegalArgumentException("invalid playerId: " + playerId);
        }
    }
}
