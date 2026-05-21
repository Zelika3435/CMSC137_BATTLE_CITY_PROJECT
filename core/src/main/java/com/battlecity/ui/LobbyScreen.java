package com.battlecity.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.battlecity.core.AppPhase;
import com.battlecity.core.PhaseContext;
import com.battlecity.core.PhaseHandler;
import com.battlecity.net.client.GameClient;
import com.battlecity.net.client.LobbySnapshot;
import com.battlecity.net.protocol.LobbyPhase;
import com.battlecity.net.protocol.NetMessages;

/**
 * Phase driver for {@link AppPhase#MP_LOBBY}.
 *
 * <p>Covers three sub-states without a phase change:
 * <ol>
 *   <li><b>Connecting</b> — not yet received JOIN_ACK; retries every second via
 *       {@link GameClient#endTick()}.
 *   <li><b>Lobby</b> — shows player roster, ready flags, and host/countdown controls while
 *       polling {@link LobbySnapshot} updates from the server.
 *   <li><b>Countdown</b> — same roster view; countdown seconds derived purely from
 *       {@link LobbySnapshot#countdownTicksLeft()} (server tick), no wall-clock interpolation.
 * </ol>
 *
 * <p>Controls (available once connected):
 * <ul>
 *   <li>{@code R} — toggle own ready state (edge-triggered; server confirms via next
 *       LOBBY_STATE broadcast).
 *   <li>{@code ENTER} / {@code SPACE} / numpad {@code ENTER} — force-start the match (host only,
 *       while server phase is LOBBY or COUNTDOWN; server validates authority).
 *   <li>{@code ESC} — send {@code DISCONNECT} then return to the main menu.
 * </ul>
 *
 * <p>Transitions to {@link AppPhase#MP_MATCH} automatically when the first authoritative
 * {@link com.battlecity.game.snapshot.GameSnapshot} arrives (server entered RUNNING phase).
 *
 * <p><b>No GameSnapshot is ever drawn here.</b> All rendering is lobby UI text only.
 * Game-world drawing happens exclusively in
 * {@link com.battlecity.core.MpMatchPhaseDriver} and the single-player drivers.
 */
public final class LobbyScreen extends com.badlogic.gdx.InputAdapter implements PhaseHandler {

    private static final int MAX_PLAYERS = 4;
    /** Max chars of a player name shown in the roster before truncating with "..". */
    private static final int NAME_DISPLAY_LIMIT = 12;

    private final PhaseContext ctx;
    private final GameClient netClient;

    /**
     * LAN IP to display as a "Share IP" hint when this client is the host.
     * Null in the JOIN flow — the hint line is simply not drawn.
     * Value is passed in at construction time; never computed inside render.
     */
    private final String hostIp;

    /** Accumulated wall time — used only for the "connecting…" dot animation, not countdown. */
    private float elapsed;
    private boolean prevEscape;
    private boolean prevR;
    private boolean prevEnter;

    /**
     * Set to {@code true} the moment {@link #update} decides to transition to
     * {@link AppPhase#MP_MATCH}.  Guards {@link #onExit()} so DISCONNECT is only sent when
     * the player explicitly leaves (ESC → main menu), not when the match starts.
     */
    private boolean leavingForMatch;

    private boolean chatMode;
    private final StringBuilder chatBuffer = new StringBuilder();
    private boolean prevT;
    private final PhaseInputGate inputGate = new PhaseInputGate();

    /** Convenience constructor for the JOIN flow (no host-IP hint needed). */
    public LobbyScreen(PhaseContext ctx, GameClient netClient) {
        this(ctx, netClient, null);
    }

    /**
     * Full constructor.
     *
     * @param hostIp detected LAN IP to show as a "Share IP" hint; {@code null} suppresses the line.
     *               Only {@code CoreGame} passes a non-null value, and only when it just started
     *               an in-process server (HOST mode).
     */
    public LobbyScreen(PhaseContext ctx, GameClient netClient, String hostIp) {
        this.ctx = ctx;
        this.netClient = netClient;
        this.hostIp = hostIp;
    }

    // ---- PhaseHandler -----------------------------------------------------------------------

    @Override
    public AppPhase update(float dt) {
        elapsed += dt;
        inputGate.tick(dt);

        boolean escNow = Gdx.input.isKeyPressed(Input.Keys.ESCAPE);
        if (escNow && !prevEscape) {
            prevEscape = true;
            if (chatMode) {
                chatMode = false;
                chatBuffer.setLength(0);
            } else {
                // DISCONNECT is sent in onExit() which CoreGame calls before closing the socket.
                return AppPhase.MAIN_MENU;
            }
        }
        prevEscape = escNow;

        netClient.poll();
        netClient.endTick();

        // First SNAPSHOT → server entered RUNNING; hand off to the match driver.
        if (netClient.currentSnapshot() != null) {
            leavingForMatch = true;
            return AppPhase.MP_MATCH;
        }

        // Server rejected the join (server full, match in progress, etc.).  Return to the
        // connect form so the player can correct host/port or wait.  CoreGame will capture
        // lastError() before closing the client and pass it as a pre-filled status message.
        if (!netClient.isConnected() && netClient.lastError() != null) {
            return AppPhase.MP_CONNECT;
        }

        if (netClient.isConnected()) {
            handleLobbyKeys();
            if (chatMode && Gdx.input.getInputProcessor() != this) {
                Gdx.input.setInputProcessor(this);
            } else if (!chatMode && Gdx.input.getInputProcessor() == this) {
                Gdx.input.setInputProcessor(null);
            }
        }

        return AppPhase.MP_LOBBY;
    }

    /**
     * Renders lobby UI only — no game-world geometry, no {@link com.battlecity.game.snapshot.GameSnapshot}.
     * State is read exclusively from {@link GameClient#lobbySnapshot()} and related accessors.
     */
    @Override
    public void render() {
        final float cx = ctx.viewport().getWorldWidth() / 2f;
        final float cy = ctx.viewport().getWorldHeight() / 2f;

        // Title
        ctx.batch().setColor(1f, 0.85f, 0.1f, 1f);
        ctx.font().draw(ctx.batch(), "BATTLE CITY", cx - 52f, cy + 96f);
        ctx.batch().setColor(1f, 1f, 1f, 1f);

        if (!netClient.isConnected()) {
            renderConnecting(cx, cy);
        } else {
            LobbySnapshot snap = netClient.lobbySnapshot();
            if (snap == null) {
                renderWaitingForLobby(cx, cy);
            } else {
                renderLobby(snap, cx, cy);
            }

            String err = netClient.lastError();
            if (err != null) {
                ctx.batch().setColor(1f, 0.35f, 0.35f, 1f);
                ctx.font().draw(ctx.batch(), "Error: " + err, cx - 140f, cy - 104f);
                ctx.batch().setColor(1f, 1f, 1f, 1f);
            }
        }


    }

    /**
     * Sends a graceful {@code DISCONNECT} to the server when the player explicitly leaves
     * (ESC → main menu). Skipped when transitioning to {@link AppPhase#MP_MATCH} so the
     * client stays connected and {@link GameClient#sendInput} remains unblocked during play.
     */
    @Override
    public void onExit() {
        if (Gdx.input.getInputProcessor() == this) {
            Gdx.input.setInputProcessor(null);
        }
        if (!leavingForMatch) {
            netClient.sendDisconnect("left lobby");
        }
    }

    @Override
    public void dispose() {
        if (Gdx.input.getInputProcessor() == this) {
            Gdx.input.setInputProcessor(null);
        }
    }

    // ---- Render helpers ---------------------------------------------------------------------

    private void renderConnecting(float cx, float cy) {
        String anim = ".".repeat((int) (elapsed * 1.5f) % 4);
        ctx.font().draw(ctx.batch(), "Connecting to server" + anim, cx - 88f, cy + 60f);
    }

    private void renderWaitingForLobby(float cx, float cy) {
        String anim = ".".repeat((int) (elapsed * 1.5f) % 4);
        ctx.font().draw(ctx.batch(), "Joining lobby" + anim, cx - 56f, cy + 60f);
        ctx.batch().setColor(0.55f, 0.55f, 0.55f, 1f);
        ctx.font().draw(ctx.batch(), "Player ID: " + netClient.playerId(), cx - 52f, cy + 36f);
        ctx.batch().setColor(1f, 1f, 1f, 1f);
    }

    private void renderLobby(LobbySnapshot snap, float cx, float cy) {
        renderPhaseHeader(snap, cx, cy);
        renderRoster(snap, cx, cy);
        renderHints(snap, cx, cy);
    }

    /**
     * Draws the phase / countdown header line.
     *
     * <p>The countdown value is read directly from {@link LobbySnapshot#countdownTicksLeft()}
     * (server-authoritative tick counter divided by 60 Hz). No wall-clock interpolation is used.
     */
    private void renderPhaseHeader(LobbySnapshot snap, float cx, float cy) {
        String header;
        switch (snap.phase()) {
            case COUNTDOWN -> {
                // Server-tick countdown — countdownTicksLeft / 60 Hz = seconds remaining.
                header = String.format("--- COUNTDOWN  %.1fs ---", snap.countdownSecondsLeft());
                ctx.batch().setColor(1f, 0.75f, 0.2f, 1f);
            }
            case END -> {
                // Server finished the match and is about to return to lobby.
                header = "--- MATCH ENDED — waiting for lobby ---";
                ctx.batch().setColor(0.8f, 0.5f, 0.2f, 1f);
            }
            default -> {
                header = "--- LOBBY (" + snap.players().size() + "/" + MAX_PLAYERS + ") ---";
                ctx.batch().setColor(0.7f, 0.9f, 1f, 1f);
            }
        }
        ctx.font().draw(ctx.batch(), header, cx - 148f, cy + 68f);
        ctx.batch().setColor(1f, 1f, 1f, 1f);
    }

    /**
     * Draws one row per player using three independent draw calls so each column can carry its
     * own colour without the last-set-colour problem of a single concatenated string.
     *
     * <pre>
     *  Column A (cx-140):  ready badge [READY] / [NOT RDY]  — green / red
     *  Column B (cx-56):   slot + display name               — cyan (local), white (others)
     *  Column C (cx+108):  [HOST] label                      — yellow, only for the host
     * </pre>
     */
    private void renderRoster(LobbySnapshot snap, float cx, float cy) {
        int row = 0;
        for (NetMessages.LobbyPlayerEntry p : snap.players()) {
            float rowY = cy + 40f - row * 24f;
            renderPlayerRow(p, snap, cx, rowY);
            row++;
        }
    }

    private void renderPlayerRow(NetMessages.LobbyPlayerEntry p, LobbySnapshot snap,
                                  float cx, float rowY) {
        boolean ready  = p.ready();
        boolean isHost = p.playerId() == snap.hostPlayerId();
        boolean isMe   = p.playerId() == netClient.playerId();

        // Column A — ready badge
        if (ready) {
            ctx.batch().setColor(0.2f, 0.9f, 0.3f, 1f);
            ctx.font().draw(ctx.batch(), "[READY]  ", cx - 140f, rowY);
        } else {
            ctx.batch().setColor(0.9f, 0.4f, 0.4f, 1f);
            ctx.font().draw(ctx.batch(), "[NOT RDY]", cx - 140f, rowY);
        }

        // Column B — slot + name (cyan for local player, white for others)
        if (isMe) {
            ctx.batch().setColor(0.6f, 0.85f, 1f, 1f);
        } else {
            ctx.batch().setColor(1f, 1f, 1f, 1f);
        }
        String name = p.name();
        String display = name.length() > NAME_DISPLAY_LIMIT
                ? name.substring(0, NAME_DISPLAY_LIMIT) + ".."
                : name;
        ctx.font().draw(ctx.batch(), "[" + p.playerId() + "] " + display, cx - 56f, rowY);

        // Column C — HOST badge (independent yellow; does not bleed onto the name)
        if (isHost) {
            ctx.batch().setColor(1f, 0.85f, 0.1f, 1f);
            ctx.font().draw(ctx.batch(), "[HOST]", cx + 108f, rowY);
        }

        ctx.batch().setColor(1f, 1f, 1f, 1f);
    }

    private void renderHints(LobbySnapshot snap, float cx, float cy) {
        ctx.batch().setColor(0.7f, 0.7f, 0.7f, 1f);

        NetMessages.LobbyPlayerEntry me = snap.playerEntry(netClient.playerId());
        boolean amReady = me != null && me.ready();

        // Lower-right corner anchor — keeps all action hints away from the left-side chat panel.
        float rightX    = cx + 40f;
        float hintEnter = 80f;   // y for the ENTER / host-status line  (topmost)
        float hintEsc   = 60f;   // y for the ESC: back to menu line
        float hintR     = 40f;   // y for the R: toggle line             (lowermost)
        float hintIp    = 20f;   // y for the Share IP line (host only)

        ctx.batch().setColor(0.55f, 0.55f, 0.55f, 1f);
        ctx.font().draw(ctx.batch(), "ESC: back to menu", rightX, hintEsc);
        ctx.batch().setColor(0.7f, 0.7f, 0.7f, 1f);
        ctx.font().draw(ctx.batch(), amReady ? "R: unready" : "R: ready", rightX, hintR);

        if (netClient.isHost()) {
            if (snap.phase() == LobbyPhase.LOBBY && canForceStartMatch(snap)) {
                ctx.batch().setColor(1f, 0.85f, 0.1f, 1f);
                ctx.font().draw(ctx.batch(), "ENTER: start match", rightX, hintEnter);
            } else if (snap.phase() == LobbyPhase.COUNTDOWN) {
                ctx.batch().setColor(0.7f, 0.7f, 0.7f, 1f);
                ctx.font().draw(ctx.batch(), "Match starting...", rightX, hintEnter);
            } else if (snap.phase() == LobbyPhase.END) {
                ctx.batch().setColor(0.7f, 0.7f, 0.7f, 1f);
                ctx.font().draw(ctx.batch(), "Waiting for next lobby...", rightX, hintEnter);
            }
            if (hostIp != null) {
                ctx.batch().setColor(0.55f, 0.9f, 0.55f, 1f);
                ctx.font().draw(ctx.batch(), "Share IP: " + hostIp, rightX, hintIp);
            }
        }

        ctx.batch().setColor(1f, 1f, 1f, 1f);

        // Chat rendering
        float leftX = cx - 190f;
        int maxMsgs = 5;
        java.util.List<GameClient.ChatEntry> msgs = netClient.chatMessages();
        int numMsgs = Math.min(maxMsgs, msgs.size());
        int startIdx = msgs.size() - numMsgs;

        float bottomChatY = 60f;  // bottom message row — aligns with hintEsc
        for (int i = 0; i < numMsgs; i++) {
            GameClient.ChatEntry msg = msgs.get(startIdx + i);
            float msgY = bottomChatY + (numMsgs - 1 - i) * 16f;
            
            ctx.batch().setColor(0.6f, 0.85f, 1f, 1f); // Cyan name
            ctx.font().draw(ctx.batch(), "[" + msg.name() + "]: ", leftX, msgY);
            ctx.batch().setColor(0.95f, 0.95f, 0.95f, 1f); // Brighter white message
            ctx.font().draw(ctx.batch(), msg.message(), leftX + 90f, msgY);
        }

        float inputY = 40f;  // input line aligns with hintR (lowermost command)
        if (chatMode) {
            ctx.batch().setColor(0.4f, 1f, 0.4f, 1f); // Bright green indicator
            ctx.font().draw(ctx.batch(), "> " + chatBuffer.toString() + (elapsed % 1f < 0.5f ? "_" : ""), leftX, inputY);
        } else {
            ctx.batch().setColor(1f, 0.85f, 0.1f, 1f); // Yellow attention-grabbing color
            ctx.font().draw(ctx.batch(), "[Press T to Chat]", leftX, inputY);
        }

        ctx.batch().setColor(1f, 1f, 1f, 1f);
    }

    // ---- Key handling -----------------------------------------------------------------------

    private void handleLobbyKeys() {
        boolean rNow = Gdx.input.isKeyPressed(Input.Keys.R);
        boolean tNow = Gdx.input.isKeyPressed(Input.Keys.T);
        boolean enterNow = Gdx.input.isKeyPressed(Input.Keys.ENTER);

        if (chatMode) {
            if (enterNow && !prevEnter) {
                if (!chatBuffer.isEmpty()) {
                    netClient.sendChat(chatBuffer.toString());
                }
                chatMode = false;
                chatBuffer.setLength(0);
            }
            prevEnter = enterNow;
            return;
        }

        if (tNow && !prevT) {
            chatMode = true;
            chatBuffer.setLength(0);
        }
        prevT = tNow;
        prevEnter = enterNow;

        if (rNow && !prevR) {
            LobbySnapshot snap = netClient.lobbySnapshot();
            NetMessages.LobbyPlayerEntry me = snap == null ? null
                    : snap.playerEntry(netClient.playerId());
            netClient.sendSetReady(me == null || !me.ready());
        }
        prevR = rNow;

        if (!inputGate.isBlocking() && startMatchKeyJustPressed()) {
            LobbySnapshot snap = netClient.lobbySnapshot();
            if (snap != null && canForceStartMatch(snap) && netClient.isHost()) {
                netClient.sendStartMatch();
            }
        }
    }

    private static boolean canForceStartMatch(LobbySnapshot snap) {
        return snap.phase() == LobbyPhase.LOBBY || snap.phase() == LobbyPhase.COUNTDOWN;
    }

    private boolean startMatchKeyJustPressed() {
        return inputGate.confirmJustPressed();
    }

    @Override
    public boolean keyTyped(char character) {
        if (!chatMode) return false;

        // Handle backspace
        if (character == '\b' && !chatBuffer.isEmpty()) {
            chatBuffer.setLength(chatBuffer.length() - 1);
            return true;
        }

        // Enter and Escape are handled in handleLobbyKeys/update
        if (character == '\r' || character == '\n' || character == 27) {
            return true;
        }

        // Only append printable ASCII characters
        if (character >= 32 && character <= 126 && chatBuffer.length() < 128) {
            chatBuffer.append(character);
            return true;
        }

        return false;
    }
}