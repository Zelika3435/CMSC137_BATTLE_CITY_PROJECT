package com.battlecity.net.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Headless tests for the {@link ServerLobby} phase-state machine.
 *
 * <p>No network, no LibGDX, no OpenGL.  All tests exercise only in-memory state
 * (pure Java: game simulation + FSM).
 */
final class ServerLobbyFsmTest {

    // ---- Initial state ----------------------------------------------------------------------

    @Test
    void startsInLobby() {
        ServerLobby lobby = new ServerLobby();
        assertEquals(ServerPhase.LOBBY, lobby.phase(),
                "server must start in LOBBY before any player joins");
        assertNull(lobby.simulation(), "simulation must be null until RUNNING");
    }

    @Test
    void newLobby_hasNoPlayers() {
        ServerLobby lobby = new ServerLobby();
        assertEquals(0, lobby.playerCount());
    }

    // ---- Player join -------------------------------------------------------------------------

    @Test
    void addPlayer_assignsAscendingIds() {
        ServerLobby lobby = new ServerLobby();
        int id0 = lobby.addPlayer("Alice");
        int id1 = lobby.addPlayer("Bob");
        assertEquals(0, id0, "first joiner gets ID 0 (host)");
        assertEquals(1, id1);
    }

    @Test
    void addPlayer_rejectsWhenFull() {
        ServerLobby lobby = new ServerLobby();
        lobby.addPlayer("P0");
        lobby.addPlayer("P1");
        lobby.addPlayer("P2");
        lobby.addPlayer("P3");
        int overflow = lobby.addPlayer("P4");
        assertEquals(-1, overflow, "5th player must be rejected");
        assertEquals(4, lobby.playerCount());
    }

    @Test
    void addPlayer_rejectsDuringRunning() {
        ServerLobby lobby = lobbyInRunning(2);
        int id = lobby.addPlayer("LateJoiner");
        assertEquals(-1, id, "joining during RUNNING must be blocked");
    }

    // ---- Ready + auto-start -----------------------------------------------------------------

    @Test
    void singlePlayerReady_doesNotStartCountdown() {
        ServerLobby lobby = new ServerLobby();
        lobby.addPlayer("Solo");
        boolean phaseChanged = lobby.setReady(0, true);
        assertFalse(phaseChanged, "one player ready must not trigger countdown");
        assertEquals(ServerPhase.LOBBY, lobby.phase());
    }

    @Test
    void bothPlayersReady_triggersCountdown() {
        ServerLobby lobby = new ServerLobby();
        lobby.addPlayer("Alice");
        lobby.addPlayer("Bob");
        lobby.setReady(0, true);
        boolean changed = lobby.setReady(1, true);
        assertTrue(changed, "second player ready must trigger COUNTDOWN");
        assertEquals(ServerPhase.COUNTDOWN, lobby.phase(), "phase must be COUNTDOWN");
        assertEquals(ServerLobby.COUNTDOWN_TICKS, lobby.countdownTicksLeft(),
                "countdown must start at full value");
    }

    @Test
    void setReady_ignoredDuringRunning() {
        ServerLobby lobby = lobbyInRunning(2);
        boolean changed = lobby.setReady(0, false);
        assertFalse(changed);
        assertEquals(ServerPhase.RUNNING, lobby.phase());
    }

    // ---- COUNTDOWN → RUNNING ---------------------------------------------------------------

    @Test
    void countdown_completesAndEntersRunning_withTickZeroWorld() {
        ServerLobby lobby = new ServerLobby();
        lobby.addPlayer("Alice");
        lobby.addPlayer("Bob");
        lobby.setReady(0, true);
        lobby.setReady(1, true);
        assertEquals(ServerPhase.COUNTDOWN, lobby.phase());

        // Tick exactly COUNTDOWN_TICKS times — last tick should trigger RUNNING.
        for (int i = 0; i < ServerLobby.COUNTDOWN_TICKS; i++) {
            lobby.tick();
        }

        assertEquals(ServerPhase.RUNNING, lobby.phase(), "phase must be RUNNING after countdown");
        assertNotNull(lobby.simulation(), "simulation must be non-null in RUNNING");
        assertEquals(0L, lobby.simulation().tickCount(),
                "simulation must start at tick 0 when RUNNING begins");
    }

    @Test
    void firstRunningTick_advancesSimulationToTickOne() {
        ServerLobby lobby = tickToRunning(2);
        assertEquals(0L, lobby.simulation().tickCount(), "pre-condition: tick 0");
        lobby.tick();
        assertEquals(1L, lobby.simulation().tickCount(), "one RUNNING tick → tick 1");
    }

    @Test
    void countdownDecrements_correctlyEachTick() {
        ServerLobby lobby = new ServerLobby();
        lobby.addPlayer("Alice");
        lobby.addPlayer("Bob");
        lobby.setReady(0, true);
        lobby.setReady(1, true);
        assertEquals(ServerLobby.COUNTDOWN_TICKS, lobby.countdownTicksLeft());
        lobby.tick();
        assertEquals(ServerLobby.COUNTDOWN_TICKS - 1, lobby.countdownTicksLeft());
    }

    // ---- Force start (host) -----------------------------------------------------------------

    @Test
    void hostForceStart_fromLobby_skipsCountdown() {
        ServerLobby lobby = new ServerLobby();
        lobby.addPlayer("Host");
        lobby.addPlayer("Guest");
        boolean accepted = lobby.forceStart(0);
        assertTrue(accepted, "host force-start must be accepted");
        assertEquals(ServerPhase.RUNNING, lobby.phase(), "must jump straight to RUNNING");
        assertNotNull(lobby.simulation());
        assertEquals(0L, lobby.simulation().tickCount());
    }

    @Test
    void hostForceStart_fromCountdown_skipsRemainder() {
        ServerLobby lobby = new ServerLobby();
        lobby.addPlayer("Host");
        lobby.addPlayer("Guest");
        lobby.setReady(0, true);
        lobby.setReady(1, true);
        // Tick a few countdown frames then force-start.
        lobby.tick();
        lobby.tick();
        boolean accepted = lobby.forceStart(0);
        assertTrue(accepted);
        assertEquals(ServerPhase.RUNNING, lobby.phase());
    }

    @Test
    void nonHostForceStart_isRejected() {
        ServerLobby lobby = new ServerLobby();
        lobby.addPlayer("Host");
        lobby.addPlayer("Guest");
        boolean accepted = lobby.forceStart(1);
        assertFalse(accepted, "only the host (lowest ID) may force-start");
        assertEquals(ServerPhase.LOBBY, lobby.phase());
    }

    // ---- Disconnect during COUNTDOWN (rollback) ---------------------------------------------

    @Test
    void disconnectDuringCountdown_belowQuorum_rollsBackToLobby() {
        ServerLobby lobby = new ServerLobby();
        lobby.addPlayer("Alice");
        lobby.addPlayer("Bob");
        lobby.setReady(0, true);
        lobby.setReady(1, true);
        assertEquals(ServerPhase.COUNTDOWN, lobby.phase());

        lobby.removePlayer(1);

        assertEquals(ServerPhase.LOBBY, lobby.phase(),
                "losing quorum during COUNTDOWN must roll back to LOBBY");
        assertNull(lobby.simulation(), "simulation must remain null after rollback");
    }

    @Test
    void disconnectDuringCountdown_quorumMaintained_staysInCountdown() {
        ServerLobby lobby = new ServerLobby();
        lobby.addPlayer("P0");
        lobby.addPlayer("P1");
        lobby.addPlayer("P2");
        lobby.setReady(0, true);
        lobby.setReady(1, true);
        lobby.setReady(2, true);
        assertEquals(ServerPhase.COUNTDOWN, lobby.phase());

        // Remove one of three — quorum (≥ 2) maintained.
        lobby.removePlayer(2);

        assertEquals(ServerPhase.COUNTDOWN, lobby.phase(),
                "two remaining players keeps quorum; countdown must continue");
    }

    @Test
    void removedSlot_isRecycled_forNextJoiner() {
        ServerLobby lobby = new ServerLobby();
        int idAlice = lobby.addPlayer("Alice");
        int idBob   = lobby.addPlayer("Bob");
        lobby.removePlayer(idAlice);

        int idCarol = lobby.addPlayer("Carol");
        assertEquals(idAlice, idCarol,
                "slot freed by Alice must be the lowest free ID and reassigned to Carol");
    }

    // ---- RUNNING → END → LOBBY (full match cycle) ------------------------------------------

    @Test
    void matchOver_transitionsToEnd() {
        ServerLobby lobby = tickToRunning(2);
        assertEquals(ServerPhase.RUNNING, lobby.phase());

        // Force match-over by directly marking the world (white-box, acceptable in test).
        lobby.simulation().world().matchOver = true;
        lobby.tick();

        assertEquals(ServerPhase.END, lobby.phase(), "matchOver flag must trigger END phase");
        assertNotNull(lobby.simulation(),
                "simulation kept alive in END so match results remain readable");
    }

    @Test
    void endPhase_expiresAndReturnsToLobby() {
        ServerLobby lobby = tickToRunning(2);
        lobby.simulation().world().matchOver = true;
        lobby.tick();
        assertEquals(ServerPhase.END, lobby.phase());

        for (int i = 0; i < ServerLobby.END_TICKS; i++) {
            lobby.tick();
        }

        assertEquals(ServerPhase.LOBBY, lobby.phase(),
                "END phase must auto-reset to LOBBY after END_TICKS ticks");
        assertNull(lobby.simulation(), "simulation must be cleared after END resets to LOBBY");
    }

    @Test
    void afterEndReset_readyFlagsCleared() {
        ServerLobby lobby = new ServerLobby();
        lobby.addPlayer("P0");
        lobby.addPlayer("P1");
        lobby.setReady(0, true);
        lobby.setReady(1, true);
        // Run through the full cycle to RUNNING then END then back to LOBBY.
        runFullCycle(lobby);

        assertEquals(ServerPhase.LOBBY, lobby.phase());
        for (ServerLobby.FsmPlayer p : lobby.playerById.values()) {
            assertFalse(p.ready, "ready flags must be cleared after END reset");
        }
    }

    @Test
    void afterEndReset_playersCanReadyAndStartAgain() {
        ServerLobby lobby = new ServerLobby();
        lobby.addPlayer("P0");
        lobby.addPlayer("P1");
        lobby.setReady(0, true);
        lobby.setReady(1, true);
        runFullCycle(lobby);

        // Both players re-ready → should enter COUNTDOWN again.
        lobby.setReady(0, true);
        lobby.setReady(1, true);
        assertEquals(ServerPhase.COUNTDOWN, lobby.phase(),
                "players must be able to start another match after END reset");
    }

    // ---- Helpers ---------------------------------------------------------------------------

    /**
     * Creates a lobby with {@code players} joined players, all ready, and ticks through
     * the full COUNTDOWN so the returned lobby is in RUNNING at tick 0.
     */
    private static ServerLobby tickToRunning(int players) {
        ServerLobby lobby = new ServerLobby();
        for (int i = 0; i < players; i++) {
            lobby.addPlayer("P" + i);
        }
        for (int i = 0; i < players; i++) {
            lobby.setReady(i, true);
        }
        for (int i = 0; i < ServerLobby.COUNTDOWN_TICKS; i++) {
            lobby.tick();
        }
        assertEquals(ServerPhase.RUNNING, lobby.phase(), "helper: must be RUNNING");
        return lobby;
    }

    /**
     * Creates a lobby already in RUNNING (using force-start to skip countdown),
     * with {@code players} joined and no ready flags needed.
     */
    private static ServerLobby lobbyInRunning(int players) {
        ServerLobby lobby = new ServerLobby();
        for (int i = 0; i < players; i++) {
            lobby.addPlayer("P" + i);
        }
        lobby.forceStart(0);
        return lobby;
    }

    /**
     * Runs an existing lobby (already in COUNTDOWN or RUNNING) through RUNNING → END → LOBBY.
     * Assumes at least 2 players are present.
     */
    private static void runFullCycle(ServerLobby lobby) {
        // If still in LOBBY/COUNTDOWN, advance to RUNNING.
        if (lobby.phase() == ServerPhase.LOBBY || lobby.phase() == ServerPhase.COUNTDOWN) {
            lobby.forceStart(0);
        }
        assertEquals(ServerPhase.RUNNING, lobby.phase());

        // Force match-over.
        lobby.simulation().world().matchOver = true;
        lobby.tick(); // RUNNING → END

        // Drain END phase.
        for (int i = 0; i < ServerLobby.END_TICKS; i++) {
            lobby.tick();
        }
        assertEquals(ServerPhase.LOBBY, lobby.phase());
    }
}
