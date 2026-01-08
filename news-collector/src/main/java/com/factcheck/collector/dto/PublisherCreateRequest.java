package com.factcheck.collector.dto;

import jakarta.validation.constraints.NotBlank;

public record PublisherCreateRequest(
        @NotBlank String name,
        String countryCode,
        String websiteUrl,
        Long mbfcSourceId
) {
}