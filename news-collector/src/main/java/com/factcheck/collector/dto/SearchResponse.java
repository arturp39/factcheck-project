package com.factcheck.collector.dto;

import java.util.List;

public record SearchResponse(
        List<ChunkResult> results,
        Integer totalFound,
        Long executionTimeMs,
        String correlationId
) {
}