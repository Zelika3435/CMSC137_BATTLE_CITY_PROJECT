package com.battlecity.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.battlecity.game.event.BaseHit;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SimTest {
    @Test
    void step_advancesTick_andMovesPlayer() {
        Simulation simulation = new Simulation(World.createDefault(), false, 0L);
        long t0 = simulation.tickCount();
        float x0 = simulation.world().tanks[0].x;

        simulation.applyCommands(List.of(
                new QueuedCommand(0, 0L, 1, GameCommand.MOVE_DIR, Direction.RIGHT)));
        simulation.updateTick();

        assertEquals(t0 + 1, simulation.tickCount());
        assertTrue(simulation.world().tanks[0].x > x0);
    }

    @Test
    void sixtySteps_movesAbout120Units() {
        Simulation simulation = new Simulation(World.createDefault(), false, 0L);
        World world = simulation.world();
        for (Tank tank : world.tanks) {
            if (tank != null && tank.playerId != 0) {
                tank.alive = false;
            }
        }
        clearMap(world);
        Tank player = world.tanks[0];
        float startY = player.y;
        for (int i = 0; i < 60; i++) {
            long tick = simulation.tickCount();
            simulation.applyCommands(List.of(
                    new QueuedCommand(0, tick, i + 1, GameCommand.MOVE_DIR, Direction.UP)));
            simulation.updateTick();
        }
        assertEquals(startY + 120f, player.y, 0.01f);
    }

    @Test
    void movingIntoBrickWall_stopsAtTileBoundary() {
        Simulation simulation = new Simulation(World.createDefault(), false, 0L);
        World world = simulation.world();
        for (Tank tank : world.tanks) {
            if (tank != null && tank.playerId != 0) {
                tank.alive = false;
            }
        }

        int wallTx = 5;
        int wallTy = 4;
        world.map.setTile(wallTx, wallTy, Tile.BRICK);
        float tile = world.map.tileSize();
        float wallLeft = wallTx * tile;
        Tank player = world.tanks[0];
        player.y = wallTy * tile + tile / 2f;
        player.x = wallLeft - player.halfW - 2f;
        player.prevX = player.x;
        player.prevY = player.y;

        for (int i = 0; i < 10; i++) {
            long tick = simulation.tickCount();
            simulation.applyCommands(List.of(
                    new QueuedCommand(0, tick, i + 1, GameCommand.MOVE_DIR, Direction.RIGHT)));
            simulation.updateTick();
        }

        assertEquals(wallLeft - player.halfW, player.x, 0.0001f);
    }

    @Test
    void projectileIntoBrick_destroysBrick() {
        Simulation simulation = new Simulation(World.createDefault(), false, 0L);
        World world = simulation.world();
        for (Tank tank : world.tanks) {
            if (tank != null && tank.playerId != 0) {
                tank.alive = false;
            }
        }
        clearMap(world);

        int wallTx = 8;
        int wallTy = 8;
        world.map.setTile(wallTx, wallTy, Tile.BRICK);
        Tank player = world.tanks[0];
        player.dir = Direction.RIGHT;
        player.x = wallTx * world.map.tileSize() - player.halfW - 10f;
        player.y = wallTy * world.map.tileSize() + world.map.tileSize() / 2f;

        simulation.applyCommands(List.of(new QueuedCommand(0, 0L, 1, GameCommand.FIRE, null)));
        simulation.updateTick();
        for (int i = 0; i < 120; i++) {
            simulation.updateTick();
            if (world.map.tileAt(wallTx, wallTy) == Tile.EMPTY) {
                break;
            }
        }
        assertEquals(Tile.EMPTY, world.map.tileAt(wallTx, wallTy));
    }

    @Test
    void baseHit_emitsEventAndEndsMatch() {
        Simulation simulation = new Simulation(World.createDefault(), false, 0L);
        World world = simulation.world();
        for (Tank tank : world.tanks) {
            if (tank != null && tank.playerId != 0) {
                tank.alive = false;
            }
        }
        clearMap(world);

        int baseX = 12;
        int baseY = 2;
        world.map.setTile(baseX, baseY, Tile.BASE);
        Tank player = world.tanks[0];
        player.dir = Direction.UP;
        player.x = baseX * world.map.tileSize() + world.map.tileSize() / 2f;
        player.y = baseY * world.map.tileSize() - 20f;

        simulation.applyCommands(List.of(new QueuedCommand(0, 0L, 1, GameCommand.FIRE, null)));
        simulation.updateTick();
        for (int i = 0; i < 200; i++) {
            simulation.updateTick();
            if (world.matchOver) {
                break;
            }
        }

        assertTrue(world.matchOver);
        assertTrue(world.baseDestroyed);
        boolean sawBaseHit = simulation.lastAppliedEvents().stream().anyMatch(BaseHit.class::isInstance);
        assertTrue(sawBaseHit);
    }

    @Test
    void playerBullet_killsEnemyTank() {
        Simulation simulation = new Simulation(World.createDefault(), false, 0L);
        World world = simulation.world();
        clearMap(world);
        Tank shooter = world.tanks[0];
        Tank target = world.tanks[1];
        for (Tank tank : world.tanks) {
            if (tank != null && tank.playerId > 1) {
                tank.alive = false;
            }
        }

        shooter.dir = Direction.RIGHT;
        target.x = shooter.x + 60f;
        target.y = shooter.y;
        target.prevX = target.x;
        target.prevY = target.y;

        simulation.applyCommands(List.of(new QueuedCommand(0, 0L, 1, GameCommand.FIRE, null)));
        simulation.updateTick();
        for (int i = 0; i < 300; i++) {
            simulation.updateTick();
            if (!target.alive) {
                break;
            }
        }
        assertFalse(target.alive);
    }

    private static void clearMap(World world) {
        for (int y = 0; y < world.map.heightTiles(); y++) {
            for (int x = 0; x < world.map.widthTiles(); x++) {
                world.map.setTile(x, y, Tile.EMPTY);
            }
        }
    }
}
