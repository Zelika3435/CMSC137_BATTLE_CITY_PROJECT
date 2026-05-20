package com.battlecity.game;

import com.battlecity.game.event.BaseHit;
import com.battlecity.game.event.GameEvent;
import com.battlecity.game.event.ProjectileHitTank;
import com.battlecity.game.event.TankDestroyed;
import com.battlecity.game.event.TileDestroyed;
import java.util.ArrayList;
import java.util.List;

public final class CombatSystem {
    private CombatSystem() {}

    public static List<GameEvent> processPending(World world) {
        List<GameEvent> drained = world.events.drain();
        if (drained.isEmpty()) {
            return List.of();
        }

        List<GameEvent> applied = new ArrayList<>();
        for (GameEvent event : drained) {
            if (event instanceof ProjectileHitTank hit) {
                Tank tank = world.tankByEntityId(hit.tankEntityId());
                if (tank != null && tank.alive && tank.playerId != hit.ownerPlayerId()) {
                    tank.alive = false;
                    TankDestroyed destroyed = new TankDestroyed(tank.entityId, tank.playerId);
                    world.events.emit(destroyed);
                    applied.add(destroyed);
                }
                applied.add(hit);
            } else if (event instanceof TileDestroyed destroyed) {
                world.map.setTile(destroyed.tileX(), destroyed.tileY(), Tile.EMPTY);
                applied.add(destroyed);
            } else if (event instanceof BaseHit hit) {
                world.map.setTile(hit.tileX(), hit.tileY(), Tile.EMPTY);
                world.baseDestroyed = true;
                world.matchOver = true;
                applied.add(hit);
            } else if (event instanceof TankDestroyed destroyed) {
                applied.add(destroyed);
            } else {
                throw new IllegalStateException("unknown event: " + event);
            }
        }
        return List.copyOf(applied);
    }
}
