package com.factcheck.collector.util;

import java.net.IDN;
import java.net.URI;
import java.util.Locale;

public final class DomainUtils {

    private DomainUtils() {}

    public static String normalizeDomain(String urlOrDomain) {
        if (urlOrDomain == null) return null;

        String value = urlOrDomain.trim();
        if (value.isEmpty()) return null;

        value = stripWrappingPunctuation(value);
        // Add a scheme so URI can parse bare domains.
        if (!value.contains("://")) {
            value = "http://" + value;
        }

        try {
            URI uri = URI.create(value);

            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                // Fallback when the host is missing.
                host = extractHostFallback(value);
                if (host == null || host.isBlank()) return null;
            }

            host = host.toLowerCase(Locale.ROOT);
            // Convert IDN to ASCII for stable keys.
            host = IDN.toASCII(host);

            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            // Reject clearly invalid hosts.
            if (host.isBlank() || host.contains(" ")) {
                return null;
            }

            return host;
        } catch (Exception e) {
            return null;
        }
    }

    private static String stripWrappingPunctuation(String s) {
        // Strip common wrapping punctuation (e.g. "(example.com)").
        int start = 0, end = s.length();
        while (start < end && isWrapChar(s.charAt(start))) start++;
        while (end > start && isWrapChar(s.charAt(end - 1))) end--;
        return s.substring(start, end).trim();
    }

    private static boolean isWrapChar(char c) {
        return Character.isWhitespace(c)
                || c == '"' || c == '\''
                || c == '(' || c == ')'
                || c == '[' || c == ']'
                || c == '{' || c == '}'
                || c == ',' || c == ';';
    }

    private static String extractHostFallback(String value) {
        // Fallback for odd cases where URI.getHost() is null but the input is still host-like.
        try {
            URI uri = new URI(value);
            String auth = uri.getRawAuthority();
            if (auth == null) return null;
            // Strip userinfo and port.
            int at = auth.lastIndexOf('@');
            String hostPort = (at >= 0) ? auth.substring(at + 1) : auth;
            int colon = hostPort.lastIndexOf(':');
            return (colon >= 0) ? hostPort.substring(0, colon) : hostPort;
        } catch (Exception e) {
            return null;
        }
    }
}