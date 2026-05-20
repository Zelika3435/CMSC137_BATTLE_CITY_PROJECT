package com.battlecity.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.battlecity.core.CoreGame;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Lwjgl3Launcher {
    private Lwjgl3Launcher() {}

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Battle City");
        config.setWindowedMode(26 * 16, 26 * 16);
        config.useVsync(true);

        if (isWsl()) {
            // WSL often has no usable ALSA/PipeWire device; skip audio to avoid startup noise/errors.
            config.disableAudio(true);
            System.out.println("[Battle City] WSL detected: audio disabled.");
            String display = System.getenv("DISPLAY");
            if (display == null || display.isBlank()) {
                System.err.println("[Battle City] WARNING: DISPLAY is not set. "
                        + "Use WSLg (Windows 11) or: export DISPLAY=:0");
            }
            if ("1".equals(System.getenv("LIBGL_ALWAYS_SOFTWARE"))) {
                System.out.println("[Battle City] Software OpenGL enabled (LIBGL_ALWAYS_SOFTWARE=1).");
            }
        }

        new Lwjgl3Application(new CoreGame(args), config);
    }

    static boolean isWsl() {
        if (System.getenv("WSL_DISTRO_NAME") != null || System.getenv("WSL_INTEROP") != null) {
            return true;
        }
        try {
            String version = Files.readString(Path.of("/proc/version")).toLowerCase();
            return version.contains("microsoft") || version.contains("wsl");
        } catch (IOException ex) {
            return false;
        }
    }
}
