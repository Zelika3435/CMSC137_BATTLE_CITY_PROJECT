package com.battlecity.net.server;

import com.battlecity.game.QueuedCommand;
import com.battlecity.game.Simulation;
import com.battlecity.game.World;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Pure, headless lobby + match phase state machine.
 *
 * <p>Contains no network I/O: no {@link com.battlecity.net.transport.UdpTransport}, no sockets,
 * no LibGDX.  This lets the FSM be driven and verified in headless JUnit tests without binding
 * any ports or importing any rendering classes.
 *
 * <h3>Lifecycle</h3>
 * <pre>  LOBBY → COUNTDOWN → RUNNING → END → LOBBY → …</pre>
 * <ul>
 *   <li><b>LOBBY</b>     — accepts players up to {@link com.battlecity.net.protocol.ProtocolConstants#MAX_PLAYERS};
 *       auto-transitions to COUNTDOWN when all connected players
 *       (≥ {@value #MIN_PLAYERS}) are ready.
 *   <li><b>COUNTDOWN</b> — ticks down {@value #COUNTDOWN_TICKS} ticks; rolls back to
 *       LOBBY if player count drops below {@value #MIN_PLAYERS}.
 *   <li><b>RUNNING</b>   — fresh {@link World#createDefault()} at tick 0; {@link #tick()}
 *       steps the simulation; transitions to END on match-over.
 *   <li><b>END</b>       — waits {@value #END_TICKS} ticks, then resets to LOBBY with
 *       ready flags cleared.
 * </ul>
 *
 * <p>{@link #playerById} is a {@link TreeMap} so players are always visited in ascending
 * ID order — matching the determinism guarantee of {@link GameServer}.
 */
public final class ServerLobby {

    /** Minimum players required to enter COUNTDOWN. */
    public static final int MIN_PLAYERS = 2;

    /** Ticks in COUNTDOWN before the match starts (~3 s at 60 Hz). */
    public static final int COUNTDOWN_TICKS = 3 * 60;  // 180

    /** Ticks in END before auto-reset to LOBBY (~5 s at 60 Hz). */
    public static final int END_TICKS = 5 * 60;        // 300

    /** Maximum players; mirrors {@link com.battlecity.net.protocol.ProtocolConstants#MAX_PLAYERS}. */
    private static final int MAX_PLAYERS = 4;

    // ---- Phase state -----------------------------------------------------------------------

    private ServerPhase phase = ServerPhase.LOBBY;
    private Simulation  simulation;
    private int         countdownTicks;
    private int         endTicks;

    // ---- Player management -----------------------------------------------------------------

    /**
     * Players keyed by assigned ID.  TreeMap guarantees ascending ID iteration (deterministic).
     * Package-private so {@link ServerLobbyFsmTest} can inspect contents directly.
     */
    final TreeMap<Integer, FsmPlayer> playerById = new TreeMap<>();

    /** Recycled pool of free IDs; {@link TreeSet#first()} returns the lowest free ID. */
    private final TreeSet<Integer> freeSlots = new TreeSet<>();

    // ----------------------------------------------------------------------------------------

    public ServerLobby() {
        for (int i = 0; i < MAX_PLAYERS; i++) {
            freeSlots.add(i);
        }
    }

    // ---- State queries ---------------------------------------------------------------------

    /** Current FSM phase. */
    public ServerPhase phase() { return phase; }

    /**
     * Active simulation, or {@code null} when phase is LOBBY, COUNTDOWN, or (after END reset).
     * Non-null only during {@link ServerPhase#RUNNING} and immediately after.
     */
    public Simulation simulation() { return simulation; }

    /** Remaining countdown ticks; only meaningful when phase is COUNTDOWN. */
    public int countdownTicksLeft() { return countdownTicks; }

    /** Number of players currently in the lobby. */
    public int playerCount() { return playerById.size(); }

    // ---- Player management -----------------------------------------------------------------

    /**
     * Adds a player with the given display name.  The lobby assigns the lowest free ID
     * (so the first joiner is always the host — player 0 — matching {@link GameServer}).
     *
     * @return the assigned player ID (0–3), or {@code -1} if the lobby is full or the match
     *         is already in progress (RUNNING / END).
     */
    public int addPlayer(String name) {
        if (freeSlots.isEmpty()) return -1;
        if (phase == ServerPhase.RUNNING || phase == ServerPhase.END) return -1;
        int id = freeSlots.first();
        freeSlots.remove(id);
        playerById.put(id, new FsmPlayer(id, name));
        return id;
    }

    /**
     * Removes a player.  If a COUNTDOWN is in progress and the remaining count drops
     * below {@value #MIN_PLAYERS}, the countdown is cancelled and the server rolls back
     * to LOBBY so the remaining players can re-ready.
     */
    public void removePlayer(int playerId) {
        FsmPlayer p = playerById.remove(playerId);
        if (p == null) return;
        freeSlots.add(playerId);
        if (phase == ServerPhase.COUNTDOWN && playerById.size() < MIN_PLAYERS) {
            transitionTo(ServerPhase.LOBBY);
        }
    }

    /**
     * Sets the ready flag for a player and checks the auto-start condition.
     * Has no effect outside {@link ServerPhase#LOBBY} and {@link ServerPhase#COUNTDOWN}.
     *
     * @return {@code true} if the phase changed (LOBBY → COUNTDOWN was triggered)
     */
    public boolean setReady(int playerId, boolean ready) {
        FsmPlayer p = playerById.get(playerId);
        if (p == null) return false;
        if (phase != ServerPhase.LOBBY && phase != ServerPhase.COUNTDOWN) return false;
        p.ready = ready;
        if (phase == ServerPhase.LOBBY && allReady()) {
            transitionTo(ServerPhase.COUNTDOWN);
            return true;
        }
        return false;
    }

    /**
     * Host (lowest active player ID) can skip the countdown and force an immediate start
     * from {@link ServerPhase#LOBBY} or {@link ServerPhase#COUNTDOWN}.
     *
     * @return {@code true} if the force-start was accepted and the phase changed to RUNNING
     */
    public boolean forceStart(int requestingPlayerId) {
        if (playerById.isEmpty()) return false;
        if (requestingPlayerId != playerById.firstKey()) return false;
        if (phase != ServerPhase.LOBBY && phase != ServerPhase.COUNTDOWN) return false;
        transitionTo(ServerPhase.RUNNING);
        return true;
    }

    // ---- Tick ------------------------------------------------------------------------------

    /**
     * Advances the FSM by one fixed tick.  In {@link ServerPhase#RUNNING} the simulation
     * is stepped with the supplied {@code commands} (pass an empty list for idle/AI ticks).
     *
     * @param  commands input commands for this tick (applied before the simulation step)
     * @return {@code true} if the phase changed during this tick
     */
    public boolean tick(List<QueuedCommand> commands) {
        ServerPhase before = phase;
        switch (phase) {
            case LOBBY -> { /* no-op — transitions come from setReady / forceStart */ }
            case COUNTDOWN -> {
                if (--countdownTicks <= 0) {
                    transitionTo(ServerPhase.RUNNING);
                }
            }
            case RUNNING -> {
                simulation.applyCommands(commands);
                simulation.updateTick();
                if (simulation.world().matchOver) {
                    transitionTo(ServerPhase.END);
                }
            }
            case END -> {
                if (--endTicks <= 0) {
                    transitionTo(ServerPhase.LOBBY);
                }
            }
        }
        return phase != before;
    }

    /** Convenience overload: advances one tick with no commands (lobby / idle use). */
    public boolean tick() {
        return tick(List.of());
    }

    // ---- Private ---------------------------------------------------------------------------

    private void transitionTo(ServerPhase next) {
        if (phase == next) return;
        phase = next;
        switch (next) {
            case COUNTDOWN -> countdownTicks = COUNTDOWN_TICKS;
            case RUNNING   -> simulation = new Simulation(World.createDefault(), false, 0L);
            case END       -> endTicks = END_TICKS;
            case LOBBY     -> {
                for (FsmPlayer p : playerById.values()) p.ready = false;
                simulation = null;
            }
        }
    }

    private boolean allReady() {
        if (playerById.size() < MIN_PLAYERS) return false;
        for (FsmPlayer p : playerById.values()) {
            if (!p.ready) return false;
        }
        return true;
    }

    // ---- Inner types -----------------------------------------------------------------------

    /**
     * Minimal player record used by the FSM.  No network address, no session data —
     * just the information needed for phase-transition decisions.
     */
    public static final class FsmPlayer {
        public final int    id;
        public final String name;
        public boolean ready;

        FsmPlayer(int id, String name) {
            this.id   = id;
            this.name = name;
        }
    }
}
