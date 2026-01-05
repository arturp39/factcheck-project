package com.factcheck.collector.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SearchRequest(
        @NotNull List<Double> embedding,
        @Min(1) @Max(100) Integer limit,
        @Min(0) @Max(1) Float minScore,
        SearchFilters filters
) {
    public SearchRequest {
        if (limit == null) {
            limit = 10;
        }
        if (minScore == null) {
            minScore = 0.7f;
        }
    }
}