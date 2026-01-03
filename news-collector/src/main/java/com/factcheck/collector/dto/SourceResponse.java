package com.factcheck.collector.dto;

import com.factcheck.collector.domain.enums.SourceKind;

import java.time.Instant;

public record SourceResponse(
        Long id,
        Long publisherId,
        String publisherName,
        SourceKind kind,
        String displayName,
        String rssUrl,
        String apiProvider,
        String apiQuery,
        boolean enabled,
        int fetchIntervalMinutes,
        Instant lastFetchedAt,
        Instant lastSuccessAt,
        int failureCount,
        Instant createdAt,
        Instant updatedAt
) {
}