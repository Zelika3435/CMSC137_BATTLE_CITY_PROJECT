package com.battlecity.ui;

import com.battlecity.core.PhaseContext;
import com.battlecity.game.TutorialScript;

/**
 * Renders the tutorial step-instruction HUD panel for {@link com.battlecity.core.AppPhase#TUTORIAL}.
 *
 * <p>The panel is anchored at the top of the viewport and spans its full width, sitting above the
 * 13×13 tutorial map so it never occludes gameplay.  Three visual rows are drawn inside it:
 * <ol>
 *   <li>Step counter + step title (gold), or "Tutorial complete!" (green) when done.
 *   <li>Hint text (light grey) on the left, ESC reminder (dark grey) on the right.
 *   <li>Four coloured step-progress dots showing completed (green), current (gold), upcoming (grey).
 * </ol>
 *
 * <p>Architecture: render-only — reads from an immutable {@link TutorialScript} snapshot every
 * frame.  No mutable state of its own; no simulation coupling.
 */
public final class TutorialOverlay {

    /** Total height of the background panel in world units. */
    public static final float PANEL_H = 58f;

    /** Thickness of the colour accent strip at the top of the panel. */
    private static final float ACCENT_H = 3f;

    /** Side of each square step-progress dot in world units. */
    private static final float DOT_SIZE = 8f;

    /** Horizontal gap between consecutive step dots. */
    private static final float DOT_GAP = 6f;

    private final PhaseContext ctx;

    public TutorialOverlay(PhaseContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Draws the overlay.  Must be called inside a {@code SpriteBatch.begin()} / {@code end()} pair.
     *
     * @param script current tutorial script — queried read-only every frame
     */
    public void render(TutorialScript script) {
        TutorialScript.Step step = script.currentStep();
        boolean done = script.isDone();

        float viewW  = ctx.viewport().getWorldWidth();
        float viewH  = ctx.viewport().getWorldHeight();
        float panelY = viewH - PANEL_H;

        // ---- Background panel ---------------------------------------------------------------
        ctx.batch().setColor(0.04f, 0.04f, 0.07f, 0.88f);
        ctx.batch().draw(ctx.whitePixel(), 0f, panelY, viewW, PANEL_H);

        // ---- Colour accent strip at the very top of the panel ------------------------------
        if (done) {
            ctx.batch().setColor(0.25f, 0.9f, 0.35f, 1f);   // green — completed
        } else {
            ctx.batch().setColor(0.9f, 0.68f, 0.08f, 1f);   // gold  — in progress
        }
        ctx.batch().draw(ctx.whitePixel(), 0f, viewH - ACCENT_H, viewW, ACCENT_H);
        ctx.batch().setColor(1f, 1f, 1f, 1f);

        float lx = 10f;

        // ---- Row 1: progress label + step title (or done message) -------------------------
        float row1Y = viewH - ACCENT_H - 14f;  // baseline of first text row

        if (!done) {
            String progress = "Step " + script.currentStepNumber() + " / " + TutorialScript.TOTAL_STEPS;
            ctx.batch().setColor(0.5f, 0.5f, 0.5f, 1f);
            ctx.font().draw(ctx.batch(), progress, lx, row1Y);

            ctx.batch().setColor(1f, 0.88f, 0.18f, 1f);
            ctx.font().draw(ctx.batch(), step.title(), lx + 76f, row1Y);
        } else {
            ctx.batch().setColor(0.3f, 1f, 0.42f, 1f);
            ctx.font().draw(ctx.batch(), step.title(), lx, row1Y);
        }

        // ---- Row 2: hint text (left) + ESC reminder (right) --------------------------------
        float row2Y = row1Y - 18f;

        ctx.batch().setColor(0.78f, 0.78f, 0.78f, 1f);
        ctx.font().draw(ctx.batch(), step.hint(), lx, row2Y);

        ctx.batch().setColor(0.42f, 0.42f, 0.42f, 1f);
        ctx.font().draw(ctx.batch(), "ESC: back to menu", viewW - 138f, row2Y);

        // ---- Row 3: step-progress dots -----------------------------------------------------
        float dotsY = panelY + 6f;
        renderStepDots(script.currentStepNumber(), lx, dotsY);

        ctx.batch().setColor(1f, 1f, 1f, 1f);
    }

    // ---- Private helpers --------------------------------------------------------------------

    /**
     * Draws {@link TutorialScript#TOTAL_STEPS} small square dots:
     * green for completed steps, gold for the current step, grey for upcoming steps.
     *
     * @param stepNumber 1-based current step number (from {@link TutorialScript#currentStepNumber()})
     * @param x          left edge of the first dot
     * @param y          bottom edge of the dots row
     */
    private void renderStepDots(int stepNumber, float x, float y) {
        for (int i = 1; i <= TutorialScript.TOTAL_STEPS; i++) {
            if (i < stepNumber) {
                ctx.batch().setColor(0.28f, 0.85f, 0.32f, 1f);   // completed — green
            } else if (i == stepNumber) {
                ctx.batch().setColor(1f, 0.85f, 0.15f, 1f);       // current   — gold
            } else {
                ctx.batch().setColor(0.28f, 0.28f, 0.28f, 1f);    // upcoming  — grey
            }
            ctx.batch().draw(ctx.whitePixel(), x + (i - 1) * (DOT_SIZE + DOT_GAP), y,
                    DOT_SIZE, DOT_SIZE);
        }
    }
}
