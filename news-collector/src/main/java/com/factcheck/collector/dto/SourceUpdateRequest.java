package com.factcheck.collector.dto;

import com.factcheck.collector.domain.enums.SourceKind;
public record SourceUpdateRequest(
        Long publisherId,
        String publisherName,
        String countryCode,
        String websiteUrl,
        SourceKind kind,
        String displayName,
        String rssUrl,
        String apiProvider,
        String apiQuery,
        Boolean enabled,
        Integer fetchIntervalMinutes
) {
}