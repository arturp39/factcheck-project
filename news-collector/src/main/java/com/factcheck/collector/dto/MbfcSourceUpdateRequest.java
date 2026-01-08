package com.factcheck.collector.dto;

public record MbfcSourceUpdateRequest(
        String sourceName,
        String mbfcUrl,
        String bias,
        String country,
        String factualReporting,
        String mediaType,
        String sourceUrl,
        String sourceUrlDomain,
        String credibility
) {
}