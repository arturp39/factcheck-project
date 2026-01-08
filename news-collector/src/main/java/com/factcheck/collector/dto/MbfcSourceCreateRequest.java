package com.factcheck.collector.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MbfcSourceCreateRequest(
        @NotNull Long mbfcSourceId,
        @NotBlank String sourceName,
        @NotBlank String mbfcUrl,
        String bias,
        String country,
        String factualReporting,
        String mediaType,
        String sourceUrl,
        String sourceUrlDomain,
        String credibility
) {
}