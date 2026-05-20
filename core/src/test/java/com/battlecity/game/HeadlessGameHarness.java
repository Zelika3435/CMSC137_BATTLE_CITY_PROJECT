package com.battlecity.game;

import com.battlecity.game.snapshot.GameSnapshot;
import java.util.ArrayList;
import java.util.List;

public final class HeadlessGameHarness {
    private final Simulation simulation;
    private final List<Long> hashSequence = new ArrayList<>();

    public HeadlessGameHarness(long botSeed) {
        this.simulation = new Simulation(World.createDefault(), false, botSeed);
    }

    public Simulation simulation() {
        return simulation;
    }

    public void step(List<QueuedCommand> commands) {
        simulation.applyCommands(commands);
        simulation.updateTick();
        hashSequence.add(simulation.stateHash());
    }

    public GameSnapshot snapshot() {
        return simulation.snapshot();
    }

    public List<Long> hashSequence() {
        return List.copyOf(hashSequence);
    }

    public long lastHash() {
        return hashSequence.isEmpty() ? simulation.stateHash() : hashSequence.get(hashSequence.size() - 1);
    }
}
