package com.example.demo.dto;

import java.util.List;

public record ClaimsPageResponse(
        String correlationId,
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<ClaimSummary> items
) {}
