package com.example.demo.service;

import com.example.demo.api.ClaimApiModels.*;
import com.example.demo.entity.Article;
import com.example.demo.entity.ClaimLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimApiService {

    private final ClaimService claimService;
    private final VertexAiService vertexAiService;
    @Value("${app.claim.max-length:400}")
    private int claimMaxLength;

    public VerifyResponse verify(String claim, String correlationId) {
        String cid = useCorrelationId(correlationId);
        String normalized = normalize(claim);
        validateClaim(normalized);

        ClaimLog saved = claimService.saveClaim(normalized);
        List<Article> evidence = claimService.searchEvidence(normalized, cid);
        String aiResponse = vertexAiService.askModel(normalized, evidence);
        ClaimService.ParsedAnswer parsed = claimService.storeModelAnswer(saved.getId(), aiResponse);

        return new VerifyResponse(
                cid,
                saved.getId(),
                normalized,
                parsed.verdict(),
                parsed.explanation(),
                evidence
        );
    }

    public ClaimsPageResponse listClaims(int page, int size, String correlationId) {
        String cid = useCorrelationId(correlationId);
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > 200) {
            throw new IllegalArgumentException("size must be between 1 and 200");
        }

        var result = claimService.listClaims(PageRequest.of(page, size));
        List<ClaimSummary> items = result.getContent().stream()
                .map(c -> new ClaimSummary(
                        c.getId(),
                        c.getClaimText(),
                        c.getCreatedAt(),
                        c.getVerdict(),
                        c.getExplanation()
                ))
                .toList();

        return new ClaimsPageResponse(
                cid,
                page,
                size,
                result.getTotalElements(),
                result.getTotalPages(),
                items
        );
    }

    public ClaimResponse getClaim(Long claimId, String correlationId) {
        String cid = useCorrelationId(correlationId);
        ClaimLog log = claimService.getClaim(claimId);
        return new ClaimResponse(
                cid,
                log.getId(),
                log.getClaimText(),
                log.getCreatedAt(),
                log.getVerdict(),
                log.getExplanation(),
                log.getBiasAnalysis(),
                log.getModelAnswer()
        );
    }

    public EvidenceResponse getEvidence(Long claimId, String correlationId) {
        String cid = useCorrelationId(correlationId);
        ClaimLog log = claimService.getClaim(claimId);
        List<Article> evidence = claimService.searchEvidence(log.getClaimText(), cid);
        return new EvidenceResponse(
                cid,
                claimId,
                log.getClaimText(),
                evidence
        );
    }

    public FollowupResponse followup(Long claimId, String question, String correlationId) {
        String cid = useCorrelationId(correlationId);
        String normalizedQ = normalize(question);
        if (normalizedQ.isEmpty()) {
            throw new IllegalArgumentException("Follow-up question must not be empty.");
        }

        ClaimLog log = claimService.getClaim(claimId);
        List<Article> evidence = claimService.searchEvidence(log.getClaimText(), cid);

        String answer = vertexAiService.answerFollowUp(
                log.getClaimText(),
                evidence,
                log.getVerdict(),
                log.getExplanation(),
                normalizedQ
        );

        return new FollowupResponse(
                cid,
                claimId,
                log.getClaimText(),
                log.getVerdict(),
                log.getExplanation(),
                log.getBiasAnalysis(),
                evidence,
                normalizedQ,
                answer
        );
    }

    public BiasResponse bias(Long claimId, String correlationId) {
        String cid = useCorrelationId(correlationId);

        ClaimLog log = claimService.getClaim(claimId);
        List<Article> evidence = claimService.searchEvidence(log.getClaimText(), cid);

        String biasText = vertexAiService.analyzeBias(
                log.getClaimText(),
                evidence,
                log.getVerdict()
        );

        claimService.storeBiasAnalysis(claimId, biasText);

        return new BiasResponse(
                cid,
                claimId,
                log.getClaimText(),
                log.getVerdict(),
                biasText
        );
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
}
