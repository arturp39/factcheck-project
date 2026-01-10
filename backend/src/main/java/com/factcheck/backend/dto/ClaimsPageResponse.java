package com.factcheck.backend.dto;

import java.util.List;

public record ClaimsPageResponse(
        String correlationId,
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<ClaimSummary> items
) {}
