package com.factcheck.collector.util;

import java.net.URI;

public final class DomainUtils {

    private DomainUtils() {
    }

    public static String normalizeDomain(String urlOrDomain) {
        if (urlOrDomain == null || urlOrDomain.isBlank()) {
            return null;
        }

        String value = urlOrDomain.trim();
        if (!value.contains("://")) {
            value = "http://" + value;
        }

        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return null;
            }
            host = host.toLowerCase();
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            return host;
        } catch (Exception e) {
            return null;
        }
    }
}
