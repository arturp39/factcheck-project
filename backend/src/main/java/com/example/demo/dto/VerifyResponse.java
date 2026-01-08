package com.example.demo.dto;

import java.util.List;

public record VerifyResponse(
        String correlationId,
        Long claimId,
        String claim,
        String verdict,
        String explanation,
        List<EvidenceItem> evidence
) {}
