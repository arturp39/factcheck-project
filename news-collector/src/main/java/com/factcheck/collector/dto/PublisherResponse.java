package com.factcheck.collector.dto;

import java.time.Instant;

public record PublisherResponse(
        Long id,
        String name,
        String countryCode,
        String websiteUrl,
        Long mbfcSourceId,
        Instant createdAt,
        Instant updatedAt
) {
}