package com.battlecity.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.battlecity.core.CoreGame;

public final class Lwjgl3Launcher {
  private Lwjgl3Launcher() {}

  public static void main(String[] args) {
    Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
    config.setTitle("Battle City (Starter)");
    config.setWindowedMode(26 * 16, 26 * 16);
    config.useVsync(true);

    new Lwjgl3Application(new CoreGame(), config);
  }
}

