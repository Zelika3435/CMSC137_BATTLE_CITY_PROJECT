package com.battlecity.net.server;

import com.battlecity.game.QueuedCommand;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ClientConnection {
    final InetSocketAddress address;
    final int playerId;
    final int sessionId;
    int lastSeq = -1;
    int lastAck = 0;
    long lastPingMs;
    int packetsSent;
    int packetsAcked;
    private final Set<String> appliedKeys = new HashSet<>();
    private final List<QueuedCommand> pendingInputs = new ArrayList<>();

    ClientConnection(InetSocketAddress address, int playerId, int sessionId) {
        this.address = address;
        this.playerId = playerId;
        this.sessionId = sessionId;
        this.lastPingMs = System.currentTimeMillis();
    }

    boolean registerInput(QueuedCommand command, long serverTick) {
        if (command.tickStamp() > serverTick + 2) {
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

    List<QueuedCommand> drainInputsForTick(long tick) {
        List<QueuedCommand> result = new ArrayList<>();
        pendingInputs.removeIf(cmd -> {
            if (cmd.tickStamp() == tick) {
                result.add(cmd);
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
