package com.battlecity.server;

import com.battlecity.game.time.FramePacer;
import com.battlecity.net.protocol.ProtocolConstants;
import com.battlecity.net.server.GameServer;
import java.io.IOException;

/**
 * Headless dedicated server entry-point. Runs the authoritative simulation at 60 ticks/sec
 * with no window or rendering. Accepts an optional port argument (default 9000).
 */
public final class HeadlessServer {

    private static final int TICK_RATE = 60;

    private HeadlessServer() {}

    public static void main(String[] args) {
        int port = ProtocolConstants.DEFAULT_PORT;
        if (args.length >= 1) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ex) {
                System.err.println("[Server] Invalid port: " + args[0] + ", using default " + port);
            }
        }

        try (GameServer server = new GameServer(port)) {
            System.out.println("[Server] Listening on UDP port " + port);
            System.out.println("[Server] Tick rate: " + TICK_RATE + " ticks/sec");
            System.out.println("[Server] Press Ctrl+C to stop.");

            long tickIntervalNs = 1_000_000_000L / TICK_RATE;
            long nextTickNs = System.nanoTime();

            while (server.isRunning()) {
                server.pollNetwork();

                long now = System.nanoTime();
                if (now >= nextTickNs) {
                    server.updateTick();
                    nextTickNs += tickIntervalNs;
                    if (nextTickNs < now) {
                        nextTickNs = now + tickIntervalNs;
                    }
                } else {
                    long sleepMs = (nextTickNs - now) / 1_000_000L;
                    if (sleepMs > 1) {
                        try {
                            Thread.sleep(sleepMs - 1);
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        } catch (IOException ex) {
            System.err.println("[Server] Failed to start: " + ex.getMessage());
            System.exit(1);
        }
    }
}
