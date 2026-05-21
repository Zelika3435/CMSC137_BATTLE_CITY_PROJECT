package com.battlecity.net.server;

import com.battlecity.game.QueuedCommand;
import com.battlecity.net.protocol.ProtocolConstants;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ClientConnection {
    final InetSocketAddress address;
    final int playerId;
    final int sessionId;
    final String name;
    int lastSeq = -1;
    int lastAck = 0;
    long lastPingMs;
    int packetsSent;
    int packetsAcked;
    /** Lobby ready-state; toggled by SET_READY messages. */
    boolean ready = false;
    private final Set<String> appliedKeys = new HashSet<>();
    private final List<QueuedCommand> pendingInputs = new ArrayList<>();

    ClientConnection(InetSocketAddress address, int playerId, int sessionId, String name) {
        this.address = address;
        this.playerId = playerId;
        this.sessionId = sessionId;
        this.name = name;
        this.lastPingMs = System.currentTimeMillis();
    }

    /**
     * Queues an input command for later application.
     *
     * <p>Rejects commands stamped more than {@link ProtocolConstants#MAX_INPUT_FUTURE_TICKS}
     * ticks ahead of the current server tick (guards against bogus future-stamps / clock skew).
     * Duplicate seq numbers are silently dropped via {@code appliedKeys}.
     */
    boolean registerInput(QueuedCommand command, long serverTick) {
        if (command.tickStamp() > serverTick + ProtocolConstants.MAX_INPUT_FUTURE_TICKS) {
            return false;
        }
        String key = command.playerId() + ":" + command.seq();
        if (!appliedKeys.add(key)) {
            return false;
        }
        if (command.seq() > lastSeq) {
            lastSeq = command.seq();
        }
        pendingInputs.add(command);
        return true;
    }

    /**
     * Drains and returns all pending commands whose {@code tickStamp <= tick}.
     *
     * <p>Commands stamped for the current tick or any earlier tick are all applied now —
     * this ensures late-arriving inputs (whose target tick has already passed due to network
     * delay) are still executed rather than silently dropped.
     *
     * <p>Late commands (tickStamp &lt; tick) have their tickStamp rewritten to {@code tick}
     * before being returned so that the simulation's per-tick filter in
     * {@link com.battlecity.game.Simulation#applyCommands} accepts them without modification.
     *
     * <p>Commands more than 120 ticks stale are expired and discarded without being returned.
     */
    List<QueuedCommand> drainInputsForTick(long tick) {
        List<QueuedCommand> result = new ArrayList<>();
        pendingInputs.removeIf(cmd -> {
            if (cmd.tickStamp() <= tick) {
                // Normalize late arrivals so the simulation's exact-tick filter accepts them.
                QueuedCommand normalized = cmd.tickStamp() == tick ? cmd
                        : new QueuedCommand(cmd.playerId(), tick, cmd.seq(),
                                            cmd.command(), cmd.moveDir());
                result.add(normalized);
                return true;
            }
            return cmd.tickStamp() < tick - 120;
        });
        return result;
    }

    float packetLossPercent() {
        if (packetsSent == 0) {
            return 0f;
        }
        int lost = Math.max(0, packetsSent - packetsAcked);
        return (lost * 100f) / packetsSent;
    }
}
