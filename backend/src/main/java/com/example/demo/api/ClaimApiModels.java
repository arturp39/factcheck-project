package com.example.demo.api;

import com.example.demo.entity.Article;

import java.time.LocalDateTime;
import java.util.List;

public final class ClaimApiModels {
    private ClaimApiModels() {}

    public record VerifyRequest(String claim) {}

    public record VerifyResponse(
            String correlationId,
            Long claimId,
            String claim,
            String verdict,
            String explanation,
            List<Article> evidence
    ) {}

    public record ClaimSummary(
            Long claimId,
            String claim,
            LocalDateTime createdAt,
            String verdict,
            String explanation
    ) {}

    public record ClaimsPageResponse(
            String correlationId,
            int page,
            int size,
            long totalElements,
            int totalPages,
            List<ClaimSummary> items
    ) {}

    public record ClaimResponse(
            String correlationId,
            Long claimId,
            String claim,
            LocalDateTime createdAt,
            String verdict,
            String explanation,
            String biasAnalysis,
            String modelAnswer
    ) {}

    public record EvidenceResponse(
            String correlationId,
            Long claimId,
            String claim,
            List<Article> evidence
    ) {}

    public record FollowupRequest(String question) {}

    public record FollowupResponse(
            String correlationId,
            Long claimId,
            String claim,
            String verdict,
            String explanation,
            String biasAnalysis,
            List<Article> evidence,
            String question,
            String answer
    ) {}

    public record BiasResponse(
            String correlationId,
            Long claimId,
            String claim,
            String verdict,
            String biasAnalysis
    ) {}
}
