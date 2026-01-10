package com.factcheck.backend.dto;

public record BiasResponse(
        String correlationId,
        Long claimId,
        String claim,
        String verdict,
        String biasAnalysis
) {}
