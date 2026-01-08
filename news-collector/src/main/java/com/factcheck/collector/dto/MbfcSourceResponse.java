package com.factcheck.collector.dto;

import java.time.Instant;

public record MbfcSourceResponse(
        Long mbfcSourceId,
        String sourceName,
        String mbfcUrl,
        String bias,
        String country,
        String factualReporting,
        String mediaType,
        String sourceUrl,
        String sourceUrlDomain,
        String credibility,
        Instant syncedAt
) {
}