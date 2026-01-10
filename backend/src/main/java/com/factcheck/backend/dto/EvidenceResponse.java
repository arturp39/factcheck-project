package com.factcheck.backend.dto;

import java.util.List;

public record EvidenceResponse(
        String correlationId,
        Long claimId,
        String claim,
        List<EvidenceItem> evidence
) {}
