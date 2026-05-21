package com.battlecity.net.client;

import com.battlecity.net.protocol.LobbyPhase;
import com.battlecity.net.protocol.NetMessages;
import java.util.List;

/**
 * Immutable snapshot of the server lobby state, produced from a received
 * {@link com.battlecity.net.protocol.MessageType#LOBBY_STATE} packet.
 *
 * <p>Consumed by {@link com.battlecity.ui.LobbyScreen} (and any other UI layer) to render
 * the lobby roster without coupling to wire-protocol internals.
 * All fields are immutable; {@code players} is a defensive copy.
 */
public record LobbySnapshot(
        LobbyPhase phase,
        List<NetMessages.LobbyPlayerEntry> players,
        int hostPlayerId,
        int countdownTicksLeft
) {
    /** Returns the entry for the given {@code playerId}, or {@code null} if not present. */
    public NetMessages.LobbyPlayerEntry playerEntry(int playerId) {
        for (NetMessages.LobbyPlayerEntry p : players) {
            if (p.playerId() == playerId) {
                return p;
            }
        }
        return null;
    }

    /** Seconds remaining in the countdown, derived from {@link #countdownTicksLeft()} at 60 Hz. */
    public float countdownSecondsLeft() {
        return countdownTicksLeft / 60f;
    }
}
