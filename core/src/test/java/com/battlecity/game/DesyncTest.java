package com.battlecity.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

final class DesyncTest {
    @Test
    void identicalInputs_produceIdenticalHashSequence() {
        List<QueuedCommand> commands = List.of(
                new QueuedCommand(0, 0L, 1, GameCommand.MOVE_DIR, Direction.RIGHT),
                new QueuedCommand(1, 0L, 1, GameCommand.MOVE_DIR, Direction.LEFT)
        );

        HeadlessGameHarness a = new HeadlessGameHarness(0L);
        HeadlessGameHarness b = new HeadlessGameHarness(0L);
        for (int i = 0; i < 120; i++) {
            long tick = a.simulation().tickCount();
            List<QueuedCommand> tickCommands = List.of(
                    new QueuedCommand(0, tick, i + 1, GameCommand.MOVE_DIR, Direction.RIGHT),
                    new QueuedCommand(1, tick, i + 1, GameCommand.MOVE_DIR, Direction.LEFT)
            );
            a.step(tickCommands);
            b.step(tickCommands);
        }
        assertEquals(a.hashSequence(), b.hashSequence());
    }

    @Test
    void divergentInputs_areDetectedByHashMismatch() {
        HeadlessGameHarness a = new HeadlessGameHarness(0L);
        HeadlessGameHarness b = new HeadlessGameHarness(0L);

        for (int i = 0; i < 60; i++) {
            long tick = a.simulation().tickCount();
            a.step(List.of(new QueuedCommand(0, tick, i + 1, GameCommand.MOVE_DIR, Direction.RIGHT)));
            b.step(List.of(new QueuedCommand(0, tick, i + 1, GameCommand.MOVE_DIR, Direction.LEFT)));
        }

        assertNotEquals(a.lastHash(), b.lastHash());
    }
}
