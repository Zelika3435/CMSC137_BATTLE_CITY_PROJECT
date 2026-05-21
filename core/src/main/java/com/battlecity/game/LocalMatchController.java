package com.battlecity.game;

import com.battlecity.game.event.GameEvent;
import com.battlecity.game.snapshot.GameSnapshot;
import java.util.ArrayList;
import java.util.List;

/**
 * Drives an offline (single-player / tutorial) simulation one tick at a time.
 *
 * <p>Player 0 receives human commands passed in by the caller; slots 1–3 are controlled by
 * {@link BotAI} when {@code botMode} is {@code true}. Deterministic for a given {@code botSeed}.
 *
 * <p>No LibGDX or rendering dependencies — safe to test headlessly.
 */
public final class LocalMatchController {

    /** Fixed timestep shared with {@link Simulation}. Exposed so callers need not import Simulation. */
    public static final float FIXED_DT_SECONDS = Simulation.FIXED_DT_SECONDS;

    private final Simulation simulation;
    private GameSnapshot prev;
    private GameSnapshot cur;

    private LocalMatchController(World world, boolean botMode, long botSeed, boolean tutorialMode) {
        this.simulation = new Simulation(world, botMode, botSeed, tutorialMode);
        this.cur = simulation.snapshot();
        this.prev = cur;
    }

    private LocalMatchController(World world, boolean botMode, long botSeed) {
        this(world, botMode, botSeed, false);
    }

    /**
     * Creates a single-player controller with bot enemies on slots 1–3.
     * Deterministic for the given {@code seed}.
     */
    public static LocalMatchController withBots(long seed) {
        return new LocalMatchController(World.createDefault(), true, seed);
    }

    /**
     * Creates a tutorial controller with no bot enemies (player learns controls alone).
     */
    public static LocalMatchController withoutBots() {
        return new LocalMatchController(World.createDefault(), false, 0L);
    }

    /**
     * Creates a dedicated tutorial controller using the small tutorial map
     * ({@link MapFactory#createTutorialMap()}) with only player 0 spawned.
     *
     * <p>Combine with {@link TutorialScript} to track step progression.
     */
    public static LocalMatchController forTutorial() {
        World world = new World(MapFactory.createTutorialMap());
        MapFactory.spawnTutorialTank(world);
        return new LocalMatchController(world, false, 0L, true);
    }

    public boolean tutorialMode() {
        return simulation.tutorialMode();
    }

    /**
     * Advances the simulation by exactly one fixed tick, applying human input for player 0.
     * Bot slots are updated internally by {@link BotAI} inside {@link Simulation#updateTick()}.
     *
     * @param moveDir direction to move this tick, or {@code null} for no movement
     * @param fire    {@code true} if the player fires this tick
     */
    public void tick(Direction moveDir, boolean fire) {
        List<QueuedCommand> commands = new ArrayList<>();
        long t = simulation.tickCount();
        int seq = 0;
        if (moveDir != null) {
            commands.add(new QueuedCommand(0, t, seq++, GameCommand.MOVE_DIR, moveDir));
        }
        if (fire) {
            commands.add(new QueuedCommand(0, t, seq, GameCommand.FIRE, null));
        }
        simulation.applyCommands(commands);
        prev = cur;
        simulation.updateTick();
        cur = simulation.snapshot();
    }

    /** Immutable snapshot produced by the most recent {@link #tick} (or initial state). */
    public GameSnapshot snapshot() {
        return cur;
    }

    /** Snapshot from the tick immediately before the most recent {@link #tick}. */
    public GameSnapshot previousSnapshot() {
        return prev;
    }

    public boolean isMatchOver() {
        return cur.matchOver();
    }

    public long tickCount() {
        return simulation.tickCount();
    }

    /** Deterministic state hash at the current tick — useful for desync detection. */
    public long stateHash() {
        return simulation.stateHash();
    }

    /**
     * Events produced by the most recently completed {@link #tick}.
     *
     * <p>Returned list is immutable. Consumed by {@link TutorialScript#evaluate} to detect
     * simulation events such as {@link com.battlecity.game.event.TileDestroyed}.
     */
    public List<GameEvent> lastEvents() {
        return simulation.lastAppliedEvents();
    }
}
