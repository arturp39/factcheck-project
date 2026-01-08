package com.factcheck.collector.dto;

public record MbfcSyncResponse(
        int fetched,
        int saved,
        int mapped
) {
}