package com.factcheck.backend.dto;

import java.util.List;

public record FollowupResponse(
        String correlationId,
        Long claimId,
        String claim,
        String verdict,
        String explanation,
        String biasAnalysis,
        List<EvidenceItem> evidence,
        String question,
        String answer
) {}
