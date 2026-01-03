package com.factcheck.collector.dto;

import com.factcheck.collector.domain.enums.SourceKind;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SourceCreateRequest(
        Long publisherId,
        String publisherName,
        String countryCode,
        String languageCode,
        String biasLabel,
        @Min(0) @Max(1) Double reliabilityScore,
        String websiteUrl,
        String mbfcUrl,
        @NotNull SourceKind kind,
        @NotBlank String displayName,
        String rssUrl,
        String apiProvider,
        String apiQuery,
        Boolean enabled,
        Integer fetchIntervalMinutes
) {
}
