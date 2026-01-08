package com.factcheck.collector.dto;

public record IngestionRunStartResponse(
        Long runId,
        String correlationId,
        int tasksEnqueued,
        String status
) {
}