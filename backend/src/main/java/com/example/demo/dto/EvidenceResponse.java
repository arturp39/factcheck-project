package com.example.demo.dto;

import java.util.List;

public record EvidenceResponse(
        String correlationId,
        Long claimId,
        String claim,
        List<EvidenceItem> evidence
) {}
