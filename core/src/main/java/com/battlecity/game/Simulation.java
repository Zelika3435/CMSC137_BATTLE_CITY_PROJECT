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
    private final boolean[] isBot;
    private final boolean offlineBotMode;
    private final boolean tutorialMode;
    private final BotAI botAI;
    private final TickInput[] tickInputs = new TickInput[World.MAX_PLAYERS];
    private final Set<String> appliedInputKeys = new HashSet<>();
    private final List<GameEvent> lastAppliedEvents = new ArrayList<>();

    public Simulation(World world) {
        this(world, false, 0L);
    }

    public Simulation(World world, boolean offlineBotMode, long botSeed) {
        this(world, offlineBotMode, botSeed, false);
    }

    public Simulation(World world, boolean offlineBotMode, long botSeed, boolean tutorialMode) {
        this(world, offlineBotMode ? new boolean[]{false, true, true, true} : new boolean[]{false, false, false, false}, botSeed, tutorialMode, offlineBotMode);
    }

    public Simulation(World world, boolean[] isBot, long botSeed, boolean tutorialMode, boolean offlineBotMode) {
        this.world = world;
        this.isBot = isBot;
        this.offlineBotMode = offlineBotMode;
        this.tutorialMode = tutorialMode;
        
        boolean hasBot = false;
        for (boolean b : isBot) {
            if (b) hasBot = true;
        }
        this.botAI = hasBot ? new BotAI(new SeededRng(botSeed)) : null;
        
        for (int i = 0; i < tickInputs.length; i++) {
            tickInputs[i] = new TickInput();
        }
    }

    public World world() {
        return world;
    }

    public boolean tutorialMode() {
        return tutorialMode;
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

        // Tick down respawn timers and respawn tanks if they have a base left
        for (int playerId = 0; playerId < World.MAX_PLAYERS; playerId++) {
            Tank tank = world.tanks[playerId];
            if (tank != null && !tank.alive && !tank.eliminated && tank.respawnCooldownTicks > 0) {
                tank.respawnCooldownTicks--;
                if (tank.respawnCooldownTicks == 0) {
                    if (world.hasBaseLeft(playerId)) {
                        tank.alive = true;
                        tank.x = tank.spawnX;
                        tank.y = tank.spawnY;
                        tank.prevX = tank.spawnX;
                        tank.prevY = tank.spawnY;
                        tank.dir = (playerId < 2 ? Direction.UP : Direction.DOWN);
                        tank.respawnCooldownTicks = -1;
                    } else {
                        tank.respawnCooldownTicks = -1; // Permanently dead
                        tank.eliminated = true;
                    }
                }
            }
        }

        if (botAI != null) {
            for (int i = 0; i < World.MAX_PLAYERS; i++) {
                if (!isBot[i]) continue;
                Tank enemy = world.tanks[i];
                if (enemy != null && enemy.alive) {
                    botAI.update(enemy, world, FIXED_DT_SECONDS, isBot);
                }
            }
        }

        for (int playerId = 0; playerId < World.MAX_PLAYERS; playerId++) {
            Tank tank = world.tanks[playerId];
            if (tank == null || !tank.alive) {
                continue;
            }
            if (!isBot[playerId]) {
                TickInput input = tickInputs[playerId];
                MovementSystem.moveTank(tank, input.moveDir, FIXED_DT_SECONDS, world.map);
                if (input.fire) {
                    ProjectileSystem.trySpawn(world, tank);
                }
            }
        }

        CollisionSystem.resolveTankVsTanks(world);
        ProjectileSystem.tick(world, FIXED_DT_SECONDS);
        lastAppliedEvents.addAll(CombatSystem.processPending(world));

        checkMatchOver();

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

    /**
     * Single-player vs bots: match ends when player 0 loses or all bots are eliminated.
     * Multiplayer: match ends only when at most one player remains (last standing); one player
     * losing (including the host) does not end the round for everyone else.
     */
    private void checkMatchOver() {
        if (tutorialMode) {
            if (world.baseDestroyed) {
                world.matchOver = true;
            }
            return;
        }

        if (offlineBotMode) {
            Tank p0 = world.tankByPlayerId(0);
            if (p0 != null && p0.eliminated) {
                if (!world.hasBaseLeft(0)) {
                    world.baseDestroyed = true;
                }
                world.matchOver = true;
                return;
            }
            int otherActive = 0;
            for (int i = 1; i < World.MAX_PLAYERS; i++) {
                Tank other = world.tankByPlayerId(i);
                if (other != null && !other.eliminated) {
                    otherActive++;
                }
            }
            if (otherActive == 0 && p0 != null && !p0.eliminated) {
                world.matchOver = true;
            }
        } else {
            int survivors = 0;
            for (int i = 0; i < World.MAX_PLAYERS; i++) {
                Tank tank = world.tankByPlayerId(i);
                if (tank != null && !tank.eliminated) {
                    survivors++;
                }
            }
            if (survivors <= 1) {
                world.matchOver = true;
            }
        }
    }

    private static final class TickInput {
        Direction moveDir;
        boolean fire;
    }
}
