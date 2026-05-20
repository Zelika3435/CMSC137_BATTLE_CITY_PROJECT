package com.battlecity.game.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EventQueue {
    private final List<GameEvent> pending = new ArrayList<>();

    public void emit(GameEvent event) {
        pending.add(event);
    }

    public List<GameEvent> drain() {
        if (pending.isEmpty()) {
            return List.of();
        }
        List<GameEvent> drained = List.copyOf(pending);
        pending.clear();
        return drained;
    }

    public List<GameEvent> peekPending() {
        return Collections.unmodifiableList(pending);
    }

    public void clear() {
        pending.clear();
    }
}
