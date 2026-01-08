package com.factcheck.collector.dto;

public record IngestionTaskRequest(
        Long runId,
        Long sourceEndpointId,
        String correlationId
) {
}