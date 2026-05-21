package com.battlecity.net;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

/**
 * Detects the machine's preferred site-local IPv4 address without any
 * LibGDX or display dependency — safe to call from headless server code
 * and unit tests.
 */
public final class LocalAddress {

    private LocalAddress() {}

    /**
     * Walks {@link NetworkInterface#getNetworkInterfaces()}, skips loopback
     * and inactive adapters, and returns the first site-local IPv4 address
     * found (e.g. {@code 192.168.x.x}, {@code 10.x.x.x}, {@code 172.16-31.x.x}).
     * Falls back to {@code "127.0.0.1"} when nothing suitable is found.
     */
    public static String detect() {
        try {
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (iface.isLoopback() || !iface.isUp()) {
                    continue;
                }
                for (InetAddress addr : Collections.list(iface.getInetAddresses())) {
                    if (addr.isSiteLocalAddress() && isIPv4(addr)) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
            // fall through to loopback
        }
        return "127.0.0.1";
    }

    private static boolean isIPv4(InetAddress addr) {
        // IPv4 addresses encode to exactly 4 bytes; IPv6 encode to 16.
        return addr.getAddress().length == 4;
    }
}
