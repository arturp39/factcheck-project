package com.factcheck.backend.dto;

import java.util.List;

public record VerifyResponse(
        String correlationId,
        Long claimId,
        String claim,
        String verdict,
        String explanation,
        List<EvidenceItem> evidence
) {}
