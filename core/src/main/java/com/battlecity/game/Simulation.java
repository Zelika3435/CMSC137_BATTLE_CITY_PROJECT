package com.battlecity.game;

import com.battlecity.game.event.GameEvent;
import com.battlecity.game.snapshot.GameSnapshot;
import com.battlecity.game.snapshot.SnapshotBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Simulation {
    public static final float FIXED_DT_SECONDS = 1f / 60f;

    private final World world;
    private final boolean offlineBotMode;
    private final BotAI botAI;
    private final TickInput[] tickInputs = new TickInput[World.MAX_PLAYERS];
    private final Set<String> appliedInputKeys = new HashSet<>();
    private final List<GameEvent> lastAppliedEvents = new ArrayList<>();

    public Simulation(World world) {
        this(world, false, 0L);
    }

    public Simulation(World world, boolean offlineBotMode, long botSeed) {
        this.world = world;
        this.offlineBotMode = offlineBotMode;
        this.botAI = offlineBotMode ? new BotAI(new SeededRng(botSeed)) : null;
        for (int i = 0; i < tickInputs.length; i++) {
            tickInputs[i] = new TickInput();
        }
    }

    public World world() {
        return world;
    }

    public long tickCount() {
        return world.tickCount;
    }

    public List<GameEvent> lastAppliedEvents() {
        return List.copyOf(lastAppliedEvents);
    }

    public void applyCommands(List<QueuedCommand> commands) {
        for (int i = 0; i < tickInputs.length; i++) {
            tickInputs[i] = new TickInput();
        }

        List<QueuedCommand> sorted = new ArrayList<>(commands);
        sorted.sort(Comparator
                .comparingLong(QueuedCommand::tickStamp)
                .thenComparingInt(QueuedCommand::playerId)
                .thenComparingInt(QueuedCommand::seq));

        long tick = world.tickCount;
        appliedInputKeys.clear();

        for (QueuedCommand command : sorted) {
            if (command.tickStamp() != tick) {
                continue;
            }
            String key = command.playerId() + ":" + command.seq();
            if (!appliedInputKeys.add(key)) {
                continue;
            }
            TickInput input = tickInputs[command.playerId()];
            switch (command.command()) {
                case MOVE_DIR -> input.moveDir = command.moveDir();
                case FIRE -> input.fire = true;
            }
        }
    }

    public void updateTick() {
        if (world.matchOver) {
            world.tickCount++;
            return;
        }

        lastAppliedEvents.clear();

        if (offlineBotMode && botAI != null) {
            Tank player = world.tanks[0];
            for (int i = 1; i < World.MAX_PLAYERS; i++) {
                Tank enemy = world.tanks[i];
                if (enemy != null) {
                    botAI.update(enemy, world, FIXED_DT_SECONDS, player);
                }
            }
        }

        for (int playerId = 0; playerId < World.MAX_PLAYERS; playerId++) {
            Tank tank = world.tanks[playerId];
            if (tank == null || !tank.alive) {
                continue;
            }
            TickInput input = tickInputs[playerId];
            if (!offlineBotMode || playerId == 0) {
                MovementSystem.moveTank(tank, input.moveDir, FIXED_DT_SECONDS, world.map);
                if (input.fire) {
                    ProjectileSystem.trySpawn(world, tank);
                }
            }
        }

        CollisionSystem.resolveTankVsTanks(world);
        ProjectileSystem.tick(world, FIXED_DT_SECONDS);
        lastAppliedEvents.addAll(CombatSystem.processPending(world));
        world.tickCount++;
    }

    public GameSnapshot snapshot() {
        return SnapshotBuilder.build(world);
    }

    public long stateHash() {
        return StateHasher.hash(world);
    }

    public List<GameEvent> drainEvents() {
        return world.events.drain();
    }

    private static final class TickInput {
        Direction moveDir;
        boolean fire;
    }
}
