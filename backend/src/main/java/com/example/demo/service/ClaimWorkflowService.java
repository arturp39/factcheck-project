package com.example.demo.service;

import com.example.demo.entity.Article;
import com.example.demo.entity.ClaimFollowup;
import com.example.demo.entity.ClaimLog;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClaimWorkflowService {

    private final ClaimService claimService;
    private final VertexAiService vertexAiService;

    @Value("${app.claim.max-length:400}")
    private int claimMaxLength;

    public VerifyResult verify(String claim, String correlationId) {
        String cid = useCorrelationId(correlationId);
        String normalized = normalize(claim);
        validateClaim(normalized);

        ClaimLog saved = claimService.saveClaim(normalized);
        List<Article> evidence = claimService.searchEvidence(normalized, cid);
        String aiResponse = vertexAiService.askModel(normalized, evidence);
        ClaimService.ParsedAnswer parsed = claimService.storeModelAnswer(saved.getId(), aiResponse);

        return new VerifyResult(
                cid,
                saved.getId(),
                normalized,
                parsed.verdict(),
                parsed.explanation(),
                evidence
        );
    }

    public FollowupResult followup(Long claimId, String question, String correlationId) {
        String cid = useCorrelationId(correlationId);
        String normalizedQ = normalize(question);
        if (normalizedQ.isEmpty()) {
            throw new IllegalArgumentException("Follow-up question must not be empty.");
        }

        ClaimLog logEntry = claimService.getClaim(claimId);
        List<Article> evidence = claimService.searchEvidence(logEntry.getClaimText(), cid);

        String answer = vertexAiService.answerFollowUp(
                logEntry.getClaimText(),
                evidence,
                logEntry.getVerdict(),
                logEntry.getExplanation(),
                normalizedQ
        );

        claimService.storeFollowup(claimId, normalizedQ, answer);

        return new FollowupResult(
                cid,
                claimId,
                logEntry.getClaimText(),
                logEntry.getVerdict(),
                logEntry.getExplanation(),
                logEntry.getBiasAnalysis(),
                evidence,
                normalizedQ,
                answer
        );
    }

    public BiasResult bias(Long claimId, String correlationId) {
        String cid = useCorrelationId(correlationId);
        ClaimLog logEntry = claimService.getClaim(claimId);
        List<Article> evidence = claimService.searchEvidence(logEntry.getClaimText(), cid);

        String biasText = vertexAiService.analyzeBias(
                logEntry.getClaimText(),
                evidence,
                logEntry.getVerdict()
        );

        claimService.storeBiasAnalysis(claimId, biasText);

        return new BiasResult(
                cid,
                claimId,
                logEntry.getClaimText(),
                logEntry.getVerdict(),
                biasText,
                evidence
        );
    }

    public ClaimContext loadClaimContext(Long claimId, String correlationId) {
        String cid = useCorrelationId(correlationId);
        ClaimLog logEntry = claimService.getClaim(claimId);
        List<Article> evidence = claimService.searchEvidence(logEntry.getClaimText(), cid);

        return new ClaimContext(
                cid,
                claimId,
                logEntry.getClaimText(),
                logEntry.getCreatedAt(),
                logEntry.getVerdict(),
                logEntry.getExplanation(),
                logEntry.getBiasAnalysis(),
                evidence
        );
    }

    public ConversationHistory loadConversationHistory(Long claimId, String correlationId) {
        ClaimContext context = loadClaimContext(claimId, correlationId);
        List<ClaimFollowup> followups = claimService.listFollowups(claimId);
        return new ConversationHistory(context, followups);
    }

    public List<ClaimFollowup> listFollowups(Long claimId) {
        return claimService.listFollowups(claimId);
    }

    public List<ClaimLog> listRecentClaims(int limit) {
        if (limit < 1) {
            return List.of();
        }
        return claimService.listClaims(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();
    }

    private void validateClaim(String normalized) {
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Claim must not be empty.");
        }
        if (normalized.length() > claimMaxLength) {
            throw new IllegalArgumentException(
                    "Claim is too long. Please keep it under " + claimMaxLength + " characters."
            );
        }
    }

    private String normalize(String input) {
        return input == null ? "" : input.trim();
    }

    private String useCorrelationId(String correlationId) {
        return (correlationId != null && !correlationId.isBlank())
                ? correlationId
                : UUID.randomUUID().toString();
    }

    public record VerifyResult(
            String correlationId,
            Long claimId,
            String claim,
            String verdict,
            String explanation,
            List<Article> evidence
    ) {}

    public record FollowupResult(
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

    public record BiasResult(
            String correlationId,
            Long claimId,
            String claim,
            String verdict,
            String biasAnalysis,
            List<Article> evidence
    ) {}

    public record ClaimContext(
            String correlationId,
            Long claimId,
            String claim,
            java.time.Instant createdAt,
            String verdict,
            String explanation,
            String biasAnalysis,
            List<Article> evidence
    ) {}

    public record ConversationHistory(
            ClaimContext context,
            List<ClaimFollowup> followups
    ) {}
}
