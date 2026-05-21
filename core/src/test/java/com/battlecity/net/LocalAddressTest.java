package com.battlecity.net;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class LocalAddressTest {

    /** Matches a bare IPv4 dotted-decimal string, e.g. "192.168.1.5" or "127.0.0.1". */
    private static final String IPV4_PATTERN =
            "^(\\d{1,3}\\.){3}\\d{1,3}$";

    @Test
    void detect_returnsNonNullNonEmptyIpv4() {
        String result = LocalAddress.detect();

        assertNotNull(result, "detect() must never return null");
        assertTrue(!result.isEmpty(), "detect() must not return an empty string");
        assertTrue(
                result.matches(IPV4_PATTERN),
                "Expected an IPv4 address but got: " + result);
    }

    @Test
    void detect_octetsInValidRange() {
        String result = LocalAddress.detect();
        String[] parts = result.split("\\.");

        for (String octet : parts) {
            int value = Integer.parseInt(octet);
            assertTrue(
                    value >= 0 && value <= 255,
                    "Octet out of range [0,255]: " + octet + " in " + result);
        }
    }

    @Test
    void detect_isIdempotent() {
        // Calling detect() twice must return the same address within a JVM run.
        String first  = LocalAddress.detect();
        String second = LocalAddress.detect();
        assertTrue(first.equals(second), "detect() returned different values on consecutive calls");
    }
}
