package com.factcheck.collector.util;

import java.util.HexFormat;

public final class HashUtils {

    private HashUtils() {
    }

    public static String sha256Hex(String value) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute sha256 hash", e);
        }
    }
}
