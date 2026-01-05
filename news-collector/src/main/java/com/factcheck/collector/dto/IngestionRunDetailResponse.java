package com.factcheck.collector.dto;

import java.time.Instant;

public record IngestionRunDetailResponse(
        Long id,
        Instant startedAt,
        Instant completedAt,
        String status,
        String correlationId
) {
}
