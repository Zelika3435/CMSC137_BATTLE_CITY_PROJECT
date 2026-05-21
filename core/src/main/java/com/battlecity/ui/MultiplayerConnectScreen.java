package com.battlecity.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.battlecity.core.AppPhase;
import com.battlecity.core.PhaseContext;
import com.battlecity.core.PhaseHandler;
import com.battlecity.net.protocol.ProtocolConstants;

/**
 * Form screen for {@link AppPhase#MP_CONNECT}.
 *
 * <p>Presents four editable fields — Mode (JOIN / HOST toggle), server host, port, and display
 * name — before any network activity starts. The actual
 * {@link com.battlecity.net.client.GameClient} is constructed by {@code CoreGame} <em>after</em>
 * reading the confirmed values via {@link #selectedHost()}, {@link #selectedPort()}, and
 * {@link #selectedDisplayName()}.
 *
 * <p>Navigation:
 * <ul>
 *   <li>UP / DOWN (or W / S when not on a text field) — move between rows.
 *   <li>LEFT / RIGHT (or A / D) — toggle Join ↔ Host on the Mode row.
 *   <li>Type freely — appends characters to the focused text field.
 *   <li>BACKSPACE — deletes the last character in the focused text field.
 *   <li>ENTER — advances to the next row, or confirms when the cursor is on the Confirm row.
 *   <li>ESC — returns to {@link AppPhase#MAIN_MENU}.
 * </ul>
 *
 * <p>HOST mode is not available from the UI; the screen shows a hint to use the CLI server
 * ({@code ./gradlew :server:run}) and does not transition.
 */
public final class MultiplayerConnectScreen implements PhaseHandler {

    /** Whether the player intends to join an existing server or host one. */
    public enum Mode { JOIN, HOST }

    // ---- Field indices (used for focused row and rendering) ----------------------------------

    private static final int FIELD_MODE    = 0;
    private static final int FIELD_HOST    = 1;
    private static final int FIELD_PORT    = 2;
    private static final int FIELD_NAME    = 3;
    private static final int FIELD_CONFIRM = 4;
    private static final int NUM_FIELDS    = 5;

    // ---- Constraints -------------------------------------------------------------------------

    private static final int MAX_HOST_LEN = 64;
    private static final int MAX_PORT_LEN = 5;   // "65535"
    private static final int MAX_NAME_LEN = 16;

    // ---- State -------------------------------------------------------------------------------

    private final PhaseContext ctx;

    private Mode mode = Mode.JOIN;
    private final StringBuilder hostBuf;
    private final StringBuilder portBuf;
    private final StringBuilder nameBuf;

    private int focused = FIELD_MODE;

    /** Non-empty while an error or info banner is displayed; cleared after {@link #STATUS_TTL}. */
    private String statusMsg = "";
    private float statusTimer = 0f;
    private static final float STATUS_TTL = 3.5f;

    // ---- Text-input capture (installed while this screen is active) --------------------------

    private final InputAdapter inputCapture = new InputAdapter() {
        @Override
        public boolean keyTyped(char c) {
            // Filter control characters and non-printable chars before appending to buffers.
            if (c == '\r' || c == '\n' || c == '\b' || c == 27 || c < 32 || c >= 127) return false;
            switch (focused) {
                case FIELD_HOST -> {
                    if (hostBuf.length() < MAX_HOST_LEN) { hostBuf.append(c); return true; }
                }
                case FIELD_PORT -> {
                    if (c >= '0' && c <= '9' && portBuf.length() < MAX_PORT_LEN) {
                        portBuf.append(c); return true;
                    }
                }
                case FIELD_NAME -> {
                    if (nameBuf.length() < MAX_NAME_LEN) { nameBuf.append(c); return true; }
                }
                default -> { /* MODE and CONFIRM rows are not text fields */ }
            }
            return false;
        }

        @Override
        public boolean keyDown(int keycode) {
            if (keycode == Input.Keys.BACKSPACE) {
                switch (focused) {
                    case FIELD_HOST -> { if (hostBuf.length() > 0) hostBuf.deleteCharAt(hostBuf.length() - 1); }
                    case FIELD_PORT -> { if (portBuf.length() > 0) portBuf.deleteCharAt(portBuf.length() - 1); }
                    case FIELD_NAME -> { if (nameBuf.length() > 0) nameBuf.deleteCharAt(nameBuf.length() - 1); }
                }
                return true;
            }
            return false;
        }
    };

    // -----------------------------------------------------------------------------------------

    /**
     * Convenience constructor — uses {@code "Player"} as the default name and shows no
     * initial error.
     */
    public MultiplayerConnectScreen(PhaseContext ctx, String defaultHost, int defaultPort) {
        this(ctx, defaultHost, defaultPort, "Player", null);
    }

    /**
     * Full constructor.
     *
     * @param defaultHost  pre-filled hostname/IP (e.g. from the previous attempt)
     * @param defaultPort  pre-filled port
     * @param defaultName  pre-filled display name
     * @param initialError if non-null, displayed immediately as a red status banner so the
     *                     player knows why they were sent back from the lobby
     */
    public MultiplayerConnectScreen(PhaseContext ctx, String defaultHost, int defaultPort,
                                    String defaultName, String initialError) {
        this.ctx = ctx;
        this.hostBuf = new StringBuilder(defaultHost);
        this.portBuf = new StringBuilder(String.valueOf(defaultPort));
        this.nameBuf = new StringBuilder(defaultName != null ? defaultName : "Player");
        if (initialError != null && !initialError.isEmpty()) {
            showStatus(initialError);
        }
        Gdx.input.setInputProcessor(inputCapture);
    }

    // ---- PhaseHandler -----------------------------------------------------------------------

    @Override
    public AppPhase update(float dt) {
        if (statusTimer > 0f) statusTimer -= dt;

        // W/S navigate only when not typing into a text field (arrow keys always navigate).
        final boolean textFocused = isTextField(focused);

        if (Gdx.input.isKeyJustPressed(Input.Keys.UP)
                || (!textFocused && Gdx.input.isKeyJustPressed(Input.Keys.W))) {
            focused = (focused - 1 + NUM_FIELDS) % NUM_FIELDS;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN)
                || (!textFocused && Gdx.input.isKeyJustPressed(Input.Keys.S))) {
            focused = (focused + 1) % NUM_FIELDS;
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            return AppPhase.MAIN_MENU;
        }

        // LEFT / RIGHT (or A / D) toggle the mode when on the Mode row.
        if (focused == FIELD_MODE) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT)
                    || Gdx.input.isKeyJustPressed(Input.Keys.A)) {
                mode = Mode.JOIN;
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT)
                    || Gdx.input.isKeyJustPressed(Input.Keys.D)) {
                mode = Mode.HOST;
            }
        }

        // ENTER: advance to the next field, or confirm on the last row.
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
                || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_ENTER)) {
            if (focused == FIELD_CONFIRM) {
                return handleConfirm();
            }
            focused = (focused + 1) % NUM_FIELDS;
        }

        return AppPhase.MP_CONNECT;
    }

    @Override
    public void render() {
        final float worldW = ctx.viewport().getWorldWidth();
        final float worldH = ctx.viewport().getWorldHeight();
        final float cx = worldW / 2f;
        final float startY = worldH * 0.74f;
        final float lineH = 32f;

        // Title
        ctx.batch().setColor(1f, 0.85f, 0.1f, 1f);
        ctx.font().draw(ctx.batch(), "BATTLE CITY", cx - 52f, startY + 56f);

        ctx.batch().setColor(0.65f, 0.65f, 0.65f, 1f);
        ctx.font().draw(ctx.batch(), "--- MULTIPLAYER ---", cx - 74f, startY + 28f);

        // Mode row — show both options; mark the selected one with [*]
        String joinMark = mode == Mode.JOIN ? "*" : " ";
        String hostMark = mode == Mode.HOST ? "*" : " ";
        renderRow(FIELD_MODE, cx - 120f, startY - FIELD_MODE * lineH,
                "Mode:  [" + joinMark + "JOIN]    [" + hostMark + "HOST]");

        // Text-field rows with inline cursor
        renderRow(FIELD_HOST, cx - 120f, startY - FIELD_HOST * lineH,
                "Host:  " + hostBuf + (focused == FIELD_HOST ? "_" : ""));

        renderRow(FIELD_PORT, cx - 120f, startY - FIELD_PORT * lineH,
                "Port:  " + portBuf + (focused == FIELD_PORT ? "_" : ""));

        renderRow(FIELD_NAME, cx - 120f, startY - FIELD_NAME * lineH,
                "Name:  " + nameBuf + (focused == FIELD_NAME ? "_" : ""));

        // Confirm row
        final String confirmLabel = mode == Mode.HOST
                ? "[ HOST: run  ./gradlew :server:run  instead ]"
                : "[ CONNECT ]";
        renderRow(FIELD_CONFIRM, cx - 120f, startY - FIELD_CONFIRM * lineH, confirmLabel);

        // Status / error banner
        if (statusTimer > 0f) {
            ctx.batch().setColor(1f, 0.35f, 0.35f, 1f);
            ctx.font().draw(ctx.batch(), statusMsg, cx - 180f, startY - NUM_FIELDS * lineH - 8f);
        }

        // Footer hints — two short lines so neither overflows the viewport.
        ctx.batch().setColor(0.38f, 0.38f, 0.38f, 1f);
        ctx.font().draw(ctx.batch(), "UP/DOWN: move    ENTER: confirm",    cx - 106f, 30f);
        ctx.font().draw(ctx.batch(), "LEFT/RIGHT: toggle mode    ESC: back", cx - 124f, 14f);
    }

    @Override
    public void onExit() {
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void dispose() {}

    // ---- Accessors (read by CoreGame just before it creates the GameClient) -----------------

    /** The connection mode the player chose. */
    public Mode selectedMode() { return mode; }

    /** Trimmed server hostname or IP address. */
    public String selectedHost() { return hostBuf.toString().trim(); }

    /**
     * Validated port number; falls back to {@link ProtocolConstants#DEFAULT_PORT} if the field
     * is empty or contains an out-of-range value.
     */
    public int selectedPort() {
        try {
            int p = Integer.parseInt(portBuf.toString());
            return (p >= 1 && p <= 65535) ? p : ProtocolConstants.DEFAULT_PORT;
        } catch (NumberFormatException e) {
            return ProtocolConstants.DEFAULT_PORT;
        }
    }

    /** Trimmed display name; falls back to {@code "Player"} if blank. */
    public String selectedDisplayName() {
        String n = nameBuf.toString().trim();
        return n.isEmpty() ? "Player" : n;
    }

    // ---- Private helpers --------------------------------------------------------------------

    private AppPhase handleConfirm() {
        if (mode == Mode.HOST) {
            showStatus("HOST: start the server separately via  ./gradlew :server:run");
            return AppPhase.MP_CONNECT;
        }
        if (selectedHost().isEmpty()) {
            showStatus("Host address cannot be empty");
            focused = FIELD_HOST;
            return AppPhase.MP_CONNECT;
        }
        if (portBuf.toString().isEmpty()) {
            showStatus("Port cannot be empty");
            focused = FIELD_PORT;
            return AppPhase.MP_CONNECT;
        }
        try {
            int p = Integer.parseInt(portBuf.toString());
            if (p < 1 || p > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            showStatus("Port must be a number between 1 and 65535");
            focused = FIELD_PORT;
            return AppPhase.MP_CONNECT;
        }
        return AppPhase.MP_LOBBY;
    }

    /** Renders one labelled row, highlighted when it is the focused row. */
    private void renderRow(int fieldIdx, float x, float y, String text) {
        if (focused == fieldIdx) {
            ctx.batch().setColor(1f, 1f, 1f, 1f);
            ctx.font().draw(ctx.batch(), "> " + text, x, y);
        } else {
            ctx.batch().setColor(0.55f, 0.55f, 0.55f, 1f);
            ctx.font().draw(ctx.batch(), "  " + text, x, y);
        }
    }

    private void showStatus(String msg) {
        this.statusMsg = msg;
        this.statusTimer = STATUS_TTL;
    }

    /** Returns {@code true} for rows that accept free-text keyboard input. */
    private static boolean isTextField(int fieldIdx) {
        return fieldIdx == FIELD_HOST || fieldIdx == FIELD_PORT || fieldIdx == FIELD_NAME;
    }
}
