package com.factcheck.collector.dto;

import com.factcheck.collector.domain.enums.SourceKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SourceCreateRequest(
        Long publisherId,
        String publisherName,
        String countryCode,
        String websiteUrl,
        @NotNull SourceKind kind,
        @NotBlank String displayName,
        String rssUrl,
        String apiProvider,
        String apiQuery,
        Boolean enabled,
        Integer fetchIntervalMinutes
) {
}