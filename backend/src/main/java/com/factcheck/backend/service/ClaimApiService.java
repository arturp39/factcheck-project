package com.factcheck.backend.service;

import com.factcheck.backend.dto.*;
import com.factcheck.backend.entity.ClaimFollowup;
import com.factcheck.backend.entity.ClaimLog;
import com.factcheck.backend.security.CurrentUserService;
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
    private final CurrentUserService currentUserService;

    private static final int EVIDENCE_SNIPPET_LIMIT = 400;

    public VerifyResponse verify(String claim, String correlationId) {
        String ownerUsername = currentUserService.requireUsername();
        ClaimWorkflowService.VerifyResult result = claimWorkflowService.verify(claim, correlationId, ownerUsername);

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
        String ownerUsername = currentUserService.requireUsername();
        boolean allowAdmin = currentUserService.isAdmin();
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > 200) {
            throw new IllegalArgumentException("size must be between 1 and 200");
        }

        var result = claimService.listClaims(PageRequest.of(page, size), ownerUsername, allowAdmin);
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
        String ownerUsername = currentUserService.requireUsername();
        boolean allowAdmin = currentUserService.isAdmin();
        ClaimLog log = claimService.getClaim(claimId, ownerUsername, allowAdmin);
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
        String ownerUsername = currentUserService.requireUsername();
        boolean allowAdmin = currentUserService.isAdmin();
        ClaimWorkflowService.ClaimContext context =
                claimWorkflowService.loadClaimContext(claimId, correlationId, ownerUsername, allowAdmin);
        return new EvidenceResponse(
                context.correlationId(),
                claimId,
                context.claim(),
                toEvidenceItems(context.evidence())
        );
    }

    public ClaimHistoryResponse getHistory(Long claimId, String correlationId) {
        String ownerUsername = currentUserService.requireUsername();
        boolean allowAdmin = currentUserService.isAdmin();
        ClaimWorkflowService.ConversationHistory history =
                claimWorkflowService.loadConversationHistory(claimId, correlationId, ownerUsername, allowAdmin);
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
        String ownerUsername = currentUserService.requireUsername();
        boolean allowAdmin = currentUserService.isAdmin();
        ClaimWorkflowService.FollowupResult result =
                claimWorkflowService.followup(claimId, question, correlationId, ownerUsername, allowAdmin);

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
        String ownerUsername = currentUserService.requireUsername();
        boolean allowAdmin = currentUserService.isAdmin();
        ClaimWorkflowService.BiasResult result = claimWorkflowService.bias(claimId, correlationId, ownerUsername, allowAdmin);

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

    private List<EvidenceItem> toEvidenceItems(List<ArticleDto> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return List.of();
        }
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

    private EvidenceItem toEvidenceItem(ArticleDto article) {
        return new EvidenceItem(
                article.title(),
                article.source(),
                article.publishedAt(),
                buildSnippet(article.content())
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
