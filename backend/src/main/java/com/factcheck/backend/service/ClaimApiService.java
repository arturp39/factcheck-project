package com.factcheck.backend.service;

import com.factcheck.backend.dto.BiasResponse;
import com.factcheck.backend.dto.ClaimHistoryResponse;
import com.factcheck.backend.dto.ClaimResponse;
import com.factcheck.backend.dto.ClaimSummary;
import com.factcheck.backend.dto.ClaimsPageResponse;
import com.factcheck.backend.dto.EvidenceItem;
import com.factcheck.backend.dto.EvidenceResponse;
import com.factcheck.backend.dto.FollowupItem;
import com.factcheck.backend.dto.FollowupResponse;
import com.factcheck.backend.dto.VerifyResponse;
import com.factcheck.backend.entity.Article;
import com.factcheck.backend.entity.ClaimFollowup;
import com.factcheck.backend.entity.ClaimLog;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClaimApiService {

    private final ClaimService claimService;
    private final ClaimWorkflowService claimWorkflowService;

    private static final int EVIDENCE_SNIPPET_LIMIT = 400;

    public VerifyResponse verify(String claim, String correlationId) {
        ClaimWorkflowService.VerifyResult result = claimWorkflowService.verify(claim, correlationId);

        return new VerifyResponse(
                result.correlationId(),
                result.claimId(),
                result.claim(),
                result.verdict(),
                result.explanation(),
                toEvidenceItems(result.evidence())
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
        ClaimWorkflowService.ClaimContext context = claimWorkflowService.loadClaimContext(claimId, correlationId);
        return new EvidenceResponse(
                context.correlationId(),
                claimId,
                context.claim(),
                toEvidenceItems(context.evidence())
        );
    }

    public ClaimHistoryResponse getHistory(Long claimId, String correlationId) {
        ClaimWorkflowService.ConversationHistory history =
                claimWorkflowService.loadConversationHistory(claimId, correlationId);
        ClaimWorkflowService.ClaimContext context = history.context();

        return new ClaimHistoryResponse(
                context.correlationId(),
                context.claimId(),
                context.claim(),
                context.createdAt(),
                context.verdict(),
                context.explanation(),
                context.biasAnalysis(),
                toEvidenceItems(context.evidence()),
                toFollowupItems(history.followups())
        );
    }

    public FollowupResponse followup(Long claimId, String question, String correlationId) {
        ClaimWorkflowService.FollowupResult result =
                claimWorkflowService.followup(claimId, question, correlationId);

        return new FollowupResponse(
                result.correlationId(),
                result.claimId(),
                result.claim(),
                result.verdict(),
                result.explanation(),
                result.biasAnalysis(),
                toEvidenceItems(result.evidence()),
                result.question(),
                result.answer()
        );
    }

    public BiasResponse bias(Long claimId, String correlationId) {
        ClaimWorkflowService.BiasResult result = claimWorkflowService.bias(claimId, correlationId);

        return new BiasResponse(
                result.correlationId(),
                result.claimId(),
                result.claim(),
                result.verdict(),
                result.biasAnalysis()
        );
    }

    private String useCorrelationId(String correlationId) {
        return (correlationId != null && !correlationId.isBlank())
                ? correlationId
                : UUID.randomUUID().toString();
    }

    private List<EvidenceItem> toEvidenceItems(List<Article> evidence) {
        return evidence.stream()
                .map(this::toEvidenceItem)
                .toList();
    }

    private List<FollowupItem> toFollowupItems(List<ClaimFollowup> followups) {
        if (followups == null || followups.isEmpty()) {
            return List.of();
        }
        return followups.stream()
                .map(f -> new FollowupItem(
                        f.getQuestion(),
                        f.getAnswer(),
                        f.getCreatedAt()
                ))
                .toList();
    }

    private EvidenceItem toEvidenceItem(Article article) {
        return new EvidenceItem(
                article.getTitle(),
                article.getSource(),
                article.getPublishedAt(),
                buildSnippet(article.getContent())
        );
    }

    private String buildSnippet(String content) {
        if (content == null) {
            return null;
        }
        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() <= EVIDENCE_SNIPPET_LIMIT) {
            return trimmed;
        }
        int limit = Math.max(0, EVIDENCE_SNIPPET_LIMIT - 3);
        return trimmed.substring(0, limit) + "...";
    }
}
