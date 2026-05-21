package com.battlecity.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.battlecity.core.AppPhase;
import com.battlecity.core.PhaseContext;
import com.battlecity.core.PhaseHandler;

/**
 * Main menu phase driver ({@link AppPhase#MAIN_MENU}).
 *
 * <p>Variable-dt only — no simulation runs here. Navigation: W/UP · S/DOWN to move cursor;
 * ENTER or SPACE to select. Returns the selected {@link AppPhase} on confirm.
 */
public final class MainMenuScreen implements PhaseHandler {

    private static final String TITLE = "BATTLE CITY";
    private static final String[] ITEMS   = {"Single Player", "Multiplayer", "Tutorial", "Quit"};
    private static final AppPhase[] TARGETS = {
        AppPhase.SP_PRESTART, AppPhase.MP_CONNECT, AppPhase.TUTORIAL, AppPhase.QUIT
    };

    /** Extra vertical gap inserted before the last item (Quit) to visually separate it. */
    private static final float QUIT_EXTRA_GAP = 14f;

    private final PhaseContext ctx;
    private int selected;
    private boolean prevUp;
    private boolean prevDown;
    private boolean prevSelect;

    public MainMenuScreen(PhaseContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public AppPhase update(float dt) {
        boolean upNow = Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP);
        boolean downNow = Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN);
        boolean selectNow = Gdx.input.isKeyPressed(Input.Keys.ENTER)
                || Gdx.input.isKeyPressed(Input.Keys.SPACE);

        if (upNow && !prevUp) {
            selected = (selected - 1 + ITEMS.length) % ITEMS.length;
        }
        if (downNow && !prevDown) {
            selected = (selected + 1) % ITEMS.length;
        }
        if (selectNow && !prevSelect) {
            prevUp = upNow;
            prevDown = downNow;
            prevSelect = true;
            return TARGETS[selected];
        }

        prevUp = upNow;
        prevDown = downNow;
        prevSelect = selectNow;
        return AppPhase.MAIN_MENU;
    }

    @Override
    public void render() {
        float worldW = ctx.viewport().getWorldWidth();
        float worldH = ctx.viewport().getWorldHeight();
        float cx = worldW / 2f;
        float startY = worldH * 0.68f;
        float lineH = 30f;

        ctx.batch().setColor(1f, 0.85f, 0.1f, 1f);
        ctx.font().draw(ctx.batch(), TITLE, cx - 52f, startY + 56f);

        for (int i = 0; i < ITEMS.length; i++) {
            // Extra gap before the last item (Quit) to visually separate it from the game modes.
            float extraGap = (i == ITEMS.length - 1) ? QUIT_EXTRA_GAP : 0f;
            float y = startY - i * lineH - extraGap;
            if (i == selected) {
                ctx.batch().setColor(1f, 1f, 1f, 1f);
                ctx.font().draw(ctx.batch(), "> " + ITEMS[i], cx - 68f, y);
            } else {
                ctx.batch().setColor(0.6f, 0.6f, 0.6f, 1f);
                ctx.font().draw(ctx.batch(), "  " + ITEMS[i], cx - 68f, y);
            }
        }
    }

    @Override
    public void onExit() {}

    @Override
    public void dispose() {}
}
