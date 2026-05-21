package com.battlecity.game;

/**
 * Emitted by {@link TutorialScript} each time the player completes a tutorial step.
 *
 * <p>This is <em>not</em> a {@link com.battlecity.game.event.GameEvent} — it is a pure
 * UI-signal record produced by the script layer and consumed by the overlay renderer.
 * It has no effect on simulation state.
 */
public record TutorialStepComplete(TutorialScript.Step completed, TutorialScript.Step next) {}
