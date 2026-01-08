package com.example.demo.dto;

import java.time.Instant;
import java.util.List;

public record ClaimHistoryResponse(
        String correlationId,
        Long claimId,
        String claim,
        Instant createdAt,
        String verdict,
        String explanation,
        String biasAnalysis,
        List<EvidenceItem> evidence,
        List<FollowupItem> followups
) {}
