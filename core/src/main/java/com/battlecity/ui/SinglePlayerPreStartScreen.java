package com.battlecity.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.battlecity.core.AppPhase;
import com.battlecity.core.PhaseContext;
import com.battlecity.core.PhaseHandler;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pre-start panel for {@link AppPhase#SP_PRESTART}.
 *
 * <p>Variable-dt only — no simulation or gameplay logic here. The player picks a bot seed before
 * the match begins:
 * <ul>
 *   <li><b>Fixed Seed (42)</b> — always produces the same bot behaviour; good for practice.
 *   <li><b>Random Seed</b> — generates a new seed each time; bot routes vary.
 *   <li><b>Back</b> — returns to {@link AppPhase#MAIN_MENU}.
 * </ul>
 *
 * <p>{@code CoreGame} reads {@link #selectedSeed()} just before constructing the
 * {@link com.battlecity.game.LocalMatchController}, so the simulation is only created after
 * the player confirms.
 */
public final class SinglePlayerPreStartScreen implements PhaseHandler {

    public static final long FIXED_SEED = 42L;

    private static final String[] LABELS = {
        "Fixed Seed (" + FIXED_SEED + ")",
        "Random Seed",
        "Back"
    };

    private final PhaseContext ctx;
    private final PhaseInputGate inputGate = new PhaseInputGate();

    private int selected;
    private boolean prevUp;
    private boolean prevDown;
    private boolean prevSelect;
    private boolean prevEscape;

    /** Set when the player confirms a seed option; read by CoreGame before transitioning. */
    private long selectedSeed = FIXED_SEED;
    /** Displayed after a random seed is generated so the player can see what was chosen. */
    private String randomSeedLabel = "";

    public SinglePlayerPreStartScreen(PhaseContext ctx) {
        this.ctx = ctx;
    }

    // ---- PhaseHandler -----------------------------------------------------------------------

    @Override
    public AppPhase update(float dt) {
        inputGate.tick(dt);

        boolean upNow = Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP);
        boolean downNow = Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN);
        boolean selectNow = Gdx.input.isKeyPressed(Input.Keys.ENTER)
                || Gdx.input.isKeyPressed(Input.Keys.SPACE);
        boolean escNow = Gdx.input.isKeyPressed(Input.Keys.ESCAPE);

        if (upNow && !prevUp) {
            selected = (selected - 1 + LABELS.length) % LABELS.length;
        }
        if (downNow && !prevDown) {
            selected = (selected + 1) % LABELS.length;
        }
        if (escNow && !prevEscape) {
            prevEscape = true;
            return AppPhase.MAIN_MENU;
        }
        prevEscape = escNow;

        if (inputGate.isBlocking()) {
            prevUp = upNow;
            prevDown = downNow;
            prevSelect = selectNow;
            return AppPhase.SP_PRESTART;
        }

        if (selectNow && !prevSelect) {
            prevUp = upNow;
            prevDown = downNow;
            prevSelect = true;
            return handleSelection();
        }

        prevUp = upNow;
        prevDown = downNow;
        prevSelect = selectNow;
        return AppPhase.SP_PRESTART;
    }

    @Override
    public void render() {
        float worldW = ctx.viewport().getWorldWidth();
        float worldH = ctx.viewport().getWorldHeight();
        float cx = worldW / 2f;
        float startY = worldH * 0.65f;
        float lineH = 30f;

        // Title
        ctx.batch().setColor(1f, 0.85f, 0.1f, 1f);
        ctx.font().draw(ctx.batch(), "SINGLE PLAYER", cx - 64f, startY + 52f);

        // Sub-title
        ctx.batch().setColor(0.7f, 0.7f, 0.7f, 1f);
        ctx.font().draw(ctx.batch(), "Choose bot seed:", cx - 60f, startY + 24f);

        // Menu items
        for (int i = 0; i < LABELS.length; i++) {
            float y = startY - i * lineH;
            if (i == selected) {
                ctx.batch().setColor(1f, 1f, 1f, 1f);
                ctx.font().draw(ctx.batch(), "> " + LABELS[i], cx - 76f, y);
            } else {
                ctx.batch().setColor(0.6f, 0.6f, 0.6f, 1f);
                ctx.font().draw(ctx.batch(), "  " + LABELS[i], cx - 76f, y);
            }
        }

        // Show last generated random seed if one was produced this session
        if (!randomSeedLabel.isEmpty()) {
            ctx.batch().setColor(0.5f, 0.85f, 0.5f, 1f);
            ctx.font().draw(ctx.batch(), randomSeedLabel, cx - 120f, startY - LABELS.length * lineH - 8f);
        }

        // Footer hint
        ctx.batch().setColor(0.4f, 0.4f, 0.4f, 1f);
        ctx.font().draw(ctx.batch(),
                "W/S or UP/DOWN to navigate    ENTER/SPACE to select    ESC back",
                cx - 188f, 22f);
    }

    @Override
    public void onExit() {}

    @Override
    public void dispose() {}

    // ---- Accessors --------------------------------------------------------------------------

    /**
     * Returns the seed chosen by the player. Read by {@code CoreGame} immediately before it
     * constructs the {@link com.battlecity.game.LocalMatchController} for the match.
     */
    public long selectedSeed() {
        return selectedSeed;
    }

    // ---- Private ----------------------------------------------------------------------------

    private AppPhase handleSelection() {
        return switch (selected) {
            case 0 -> {
                // Fixed seed
                selectedSeed = FIXED_SEED;
                yield AppPhase.SINGLE_PLAYER;
            }
            case 1 -> {
                // Random seed — non-deterministic choice made here, outside the simulation
                selectedSeed = ThreadLocalRandom.current().nextLong();
                randomSeedLabel = "Random seed: " + selectedSeed;
                yield AppPhase.SINGLE_PLAYER;
            }
            default -> AppPhase.MAIN_MENU; // Back
        };
    }
}
