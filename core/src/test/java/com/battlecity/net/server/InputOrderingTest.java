package com.battlecity.net.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.battlecity.game.GameCommand;
import com.battlecity.game.QueuedCommand;
import com.battlecity.game.Direction;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

final class InputOrderingTest {
    @Test
    void duplicateSeqIsIgnored() {
        ClientConnection client = new ClientConnection(new InetSocketAddress("127.0.0.1", 1), 0, 1, "Player");
        QueuedCommand command = new QueuedCommand(0, 5L, 10, GameCommand.FIRE, null);
        assertTrue(client.registerInput(command, 5L));
        assertFalse(client.registerInput(command, 5L));
    }

    @Test
    void drainsInputsForMatchingTickOnly() {
        ClientConnection client = new ClientConnection(new InetSocketAddress("127.0.0.1", 1), 0, 1, "Player");
        client.registerInput(new QueuedCommand(0, 4L, 1, GameCommand.MOVE_DIR, Direction.LEFT), 5L);
        client.registerInput(new QueuedCommand(0, 5L, 2, GameCommand.FIRE, null), 5L);
        assertEquals(1, client.drainInputsForTick(4L).size());
        assertEquals(1, client.drainInputsForTick(5L).size());
        assertEquals(0, client.drainInputsForTick(5L).size());
    }
}
