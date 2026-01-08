package com.factcheck.collector.dto;

public record PublisherUpdateRequest(
        String name,
        String countryCode,
        String websiteUrl,
        Long mbfcSourceId
) {
}