package com.battlecity.net.server;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.battlecity.net.client.GameClient;
import com.battlecity.net.protocol.LobbyPhase;
import org.junit.jupiter.api.Test;

/**
 * Verifies that after the lobby countdown completes, connected clients receive an authoritative
 * snapshot and can enter the match phase.
 */
final class CountdownToMatchIntegrationTest {

    @Test
    void hostForceStart_deliversSnapshot() throws Exception {
        GameServer server = new GameServer(19003);
        GameClient host = new GameClient("127.0.0.1", 19003, "Host");

        try {
            host.connect();
            waitUntilConnected(server, host, 300);
            host.sendStartMatch();

            boolean gotSnap = false;
            for (int i = 0; i < 120; i++) {
                tickServer(server);
                host.poll();
                host.endTick();
                if (host.currentSnapshot() != null) {
                    gotSnap = true;
                    break;
                }
            }
            assertTrue(gotSnap, "host should receive snapshot after force-start");
        } finally {
            server.close();
            host.close();
        }
    }

    @Test
    void countdownCompletion_deliversSnapshotToClients() throws Exception {
        GameServer server = new GameServer(19002);
        GameClient host = new GameClient("127.0.0.1", 19002, "Host");
        GameClient joiner = new GameClient("127.0.0.1", 19002, "Joiner");

        try {
            host.connect();
            joiner.connect();

            waitUntilConnected(server, host, joiner, 300);
            assertTrue(host.isConnected() && joiner.isConnected(), "both clients must connect");

            host.sendSetReady(true);
            joiner.sendSetReady(true);

            boolean hostSnap = false;
            boolean joinerSnap = false;
            LobbyPhase lastPhase = null;
            int lastCountdown = -1;
            boolean sawCountdown = false;

            for (int i = 0; i < ServerLobby.COUNTDOWN_TICKS + 120; i++) {
                tickServer(server);
                host.poll();
                joiner.poll();
                host.endTick();
                joiner.endTick();

                if (host.lobbySnapshot() != null) {
                    lastPhase = host.lobbySnapshot().phase();
                    lastCountdown = host.lobbySnapshot().countdownTicksLeft();
                    if (lastPhase == LobbyPhase.COUNTDOWN) {
                        sawCountdown = true;
                    }
                }

                hostSnap = host.currentSnapshot() != null;
                joinerSnap = joiner.currentSnapshot() != null;
                if (hostSnap && joinerSnap) {
                    break;
                }
            }

            assertTrue(sawCountdown, "server should enter COUNTDOWN after both players ready");
            assertTrue(hostSnap, "host should receive snapshot after countdown (lastPhase="
                    + lastPhase + " countdown=" + lastCountdown + ")");
            assertTrue(joinerSnap, "joiner should receive snapshot after countdown");
            assertNotNull(host.currentSnapshot());
            assertNotNull(joiner.currentSnapshot());
        } finally {
            server.close();
            host.close();
            joiner.close();
        }
    }

    private static void tickServer(GameServer server) {
        server.pollNetwork();
        server.updateTick();
    }

    private static void waitUntilConnected(GameServer server, GameClient a, int maxIters)
            throws InterruptedException {
        for (int i = 0; i < maxIters; i++) {
            tickServer(server);
            a.poll();
            if (a.isConnected()) {
                return;
            }
            Thread.sleep(5);
        }
    }

    private static void waitUntilConnected(GameServer server, GameClient a, GameClient b, int maxIters)
            throws InterruptedException {
        for (int i = 0; i < maxIters; i++) {
            tickServer(server);
            a.poll();
            b.poll();
            if (a.isConnected() && b.isConnected()) {
                return;
            }
            Thread.sleep(5);
        }
    }
}
