package com.factcheck.backend.dto;

import java.time.Instant;

public record ClaimResponse(
        String correlationId,
        Long claimId,
        String claim,
        Instant createdAt,
        String verdict,
        String explanation,
        String biasAnalysis,
        String modelAnswer
) {}
