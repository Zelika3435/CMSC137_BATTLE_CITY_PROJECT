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
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            System.err.println("[Battle City] Uncaught exception on thread " + thread.getName());
            throwable.printStackTrace(System.err);
        });

        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Battle City");
        config.setWindowedMode(26 * 32, 26 * 32);
        config.setResizable(true);

        if (isWsl()) {
            config.disableAudio(true);
            config.useVsync(false);
            config.setForegroundFPS(60);
            System.out.println("[Battle City] WSL detected: audio disabled, vsync off.");
            String display = System.getenv("DISPLAY");
            if (display == null || display.isBlank()) {
                System.err.println("[Battle City] WARNING: DISPLAY is not set. "
                        + "Use WSLg (Windows 11) or: export DISPLAY=:0");
            }
            String gallium = System.getenv("GALLIUM_DRIVER");
            if ("1".equals(System.getenv("LIBGL_ALWAYS_SOFTWARE"))) {
                System.out.println("[Battle City] Software OpenGL enabled (LIBGL_ALWAYS_SOFTWARE=1).");
            } else if ("d3d12".equals(gallium)) {
                System.out.println("[Battle City] WSLg GPU (GALLIUM_DRIVER=d3d12).");
            } else if (gallium != null && !gallium.isBlank()) {
                System.out.println("[Battle City] Gallium driver: " + gallium + ".");
            }
            System.out.println("[Battle City] DISPLAY=" + String.valueOf(System.getenv("DISPLAY")));
            System.out.println("[Battle City] WAYLAND_DISPLAY="
                    + String.valueOf(System.getenv("WAYLAND_DISPLAY")));
            System.out.println("[Battle City] GLFW_PLATFORM="
                    + String.valueOf(System.getenv("GLFW_PLATFORM")));
        } else {
            config.useVsync(true);
        }

        try {
            new Lwjgl3Application(new CoreGame(args), config);
        } catch (Throwable throwable) {
            System.err.println("[Battle City] Failed to start LWJGL application");
            throwable.printStackTrace(System.err);
            throw throwable;
        }
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
