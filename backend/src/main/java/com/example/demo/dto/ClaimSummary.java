package com.example.demo.dto;

import java.time.Instant;

public record ClaimSummary(
        Long claimId,
        String claim,
        Instant createdAt,
        String verdict,
        String explanation
) {}
