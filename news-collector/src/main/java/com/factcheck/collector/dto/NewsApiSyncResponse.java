package com.factcheck.collector.dto;

public record NewsApiSyncResponse(
        int fetched,
        int created,
        int updatedPublishers,
        int skipped
) {
}