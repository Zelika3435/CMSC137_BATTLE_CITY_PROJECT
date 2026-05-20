package com.battlecity.server;

import com.battlecity.game.time.FramePacer;
import com.battlecity.net.protocol.ProtocolConstants;
import com.battlecity.net.server.GameServer;
import java.io.IOException;

public final class HeadlessServer {
    private HeadlessServer() {}

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : ProtocolConstants.DEFAULT_PORT;
        GameServer server = new GameServer(port);
        FramePacer pacer = new FramePacer(60.0);

        System.out.println("Battle City authoritative server on UDP port " + port);

        while (server.isRunning()) {
            server.pollNetwork();
            int ticks = pacer.consumeDueTicks();
            for (int i = 0; i < ticks; i++) {
                server.updateTick();
            }
            pacer.spinWaitForNextTick();
        }
    }
}
