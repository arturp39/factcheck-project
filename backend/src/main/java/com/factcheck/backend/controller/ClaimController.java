package com.factcheck.backend.controller;

import com.factcheck.backend.dto.ArticleDto;
import com.factcheck.backend.entity.ClaimFollowup;
import com.factcheck.backend.entity.ClaimLog;
import com.factcheck.backend.security.CurrentUserService;
import com.factcheck.backend.service.ClaimWorkflowService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ClaimController {

    private final ClaimWorkflowService claimWorkflowService;
    private final CurrentUserService currentUserService;
    private static final int GROUPED_CHUNK_THRESHOLD = 3;
    private static final int EVIDENCE_TOGGLE_THRESHOLD = 260;
    private static final int RECENT_CLAIMS_LIMIT = 10;

    public ClaimController(ClaimWorkflowService claimWorkflowService, CurrentUserService currentUserService) {
        this.claimWorkflowService = claimWorkflowService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/verify")
    public String verify(@RequestParam String claim, Model model) {
        try {
            String ownerUsername = currentUserService.requireUsername();
            ClaimWorkflowService.VerifyResult result = claimWorkflowService.verify(claim, null, ownerUsername);
            applyBaseModel(
                    model,
                    result.claimId(),
                    result.claim(),
                    result.evidence(),
                    result.verdict(),
                    result.explanation(),
                    null,
                    List.of()
            );
            return "result";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            String ownerUsername = currentUserService.requireUsername();
            model.addAttribute("recentClaims", claimWorkflowService.listRecentClaims(RECENT_CLAIMS_LIMIT, ownerUsername, false));
            return "index";
        }
    }

    @PostMapping("/followup/{id}")
    public String followup(@PathVariable("id") Long claimId,
                           @RequestParam(value = "question", required = false) String question,
                           Model model) {

        String normalizedQ = question == null ? "" : question.trim();
        if (normalizedQ.isEmpty()) {
            String ownerUsername = currentUserService.requireUsername();
            ClaimWorkflowService.ClaimContext context =
                    claimWorkflowService.loadClaimContext(claimId, null, ownerUsername, false);
            model.addAttribute("error", "Follow-up question must not be empty.");
            applyBaseModel(
                    model,
                    context.claimId(),
                    context.claim(),
                    context.evidence(),
                    context.verdict(),
                    context.explanation(),
                    context.biasAnalysis(),
                    claimWorkflowService.listFollowups(claimId, ownerUsername, false)
            );
            return "result";
        }

        String ownerUsername = currentUserService.requireUsername();
        ClaimWorkflowService.FollowupResult result =
                claimWorkflowService.followup(claimId, normalizedQ, null, ownerUsername, false);
        applyBaseModel(
                model,
                result.claimId(),
                result.claim(),
                result.evidence(),
                result.verdict(),
                result.explanation(),
                result.biasAnalysis(),
                claimWorkflowService.listFollowups(claimId, ownerUsername, false)
        );
        model.addAttribute("followupQuestion", result.question());
        model.addAttribute("followupAnswer", result.answer());

        return "result";
    }

    @PostMapping("/bias/{id}")
    public String analyzeBias(@PathVariable("id") Long claimId, Model model) {
        String ownerUsername = currentUserService.requireUsername();
        ClaimWorkflowService.BiasResult result = claimWorkflowService.bias(claimId, null, ownerUsername, false);
        applyBaseModel(
                model,
                result.claimId(),
                result.claim(),
                result.evidence(),
                result.verdict(),
                null,
                result.biasAnalysis(),
                claimWorkflowService.listFollowups(claimId, ownerUsername, false)
        );

        return "result";
    }

    @GetMapping("/history/{id}")
    public String history(@PathVariable("id") Long claimId, Model model) {
        String ownerUsername = currentUserService.requireUsername();
        ClaimWorkflowService.ConversationHistory history =
                claimWorkflowService.loadConversationHistory(claimId, null, ownerUsername, false);
        ClaimWorkflowService.ClaimContext context = history.context();
        applyBaseModel(
                model,
                context.claimId(),
                context.claim(),
                context.evidence(),
                context.verdict(),
                context.explanation(),
                context.biasAnalysis(),
                history.followups()
        );
        return "result";
    }

    @GetMapping("/")
    public String index(Model model) {
        String ownerUsername = currentUserService.requireUsername();
        List<ClaimLog> recentClaims = claimWorkflowService.listRecentClaims(RECENT_CLAIMS_LIMIT, ownerUsername, false);
        model.addAttribute("recentClaims", recentClaims);
        return "index";
    }

    private void applyBaseModel(
            Model model,
            Long claimId,
            String claim,
            List<ArticleDto> evidence,
            String verdict,
            String explanation,
            String biasAnalysis,
            List<ClaimFollowup> followups
    ) {
        List<ArticleDto> safeEvidence = evidence == null ? List.of() : evidence;
        model.addAttribute("claimId", claimId);
        model.addAttribute("claim", claim);
        model.addAttribute("evidence", safeEvidence);
        model.addAttribute("evidenceViews", buildEvidenceViews(safeEvidence));
        model.addAttribute("verdict", verdict);
        model.addAttribute("explanation", explanation);
        model.addAttribute("biasAnalysis", biasAnalysis);
        model.addAttribute("followups", followups == null ? List.of() : followups);
    }

    private List<EvidenceView> buildEvidenceViews(List<ArticleDto> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return List.of();
        }

        Map<String, List<ArticleDto>> grouped = new LinkedHashMap<>();
        for (ArticleDto article : evidence) {
            grouped.computeIfAbsent(groupKey(article), key -> new ArrayList<>()).add(article);
        }

        List<EvidenceView> views = new ArrayList<>();
        for (List<ArticleDto> group : grouped.values()) {
            if (group.size() >= GROUPED_CHUNK_THRESHOLD) {
                ArticleDto anchor = group.get(0);
                List<EvidenceChunkView> chunks = group.stream()
                        .map(item -> toChunkView(item.content()))
                        .toList();
                views.add(new EvidenceView(
                        anchor.title(),
                        anchor.source(),
                        anchor.publishedAt(),
                        anchor.url(),
                        chunks,
                        true,
                        chunks.size()
                ));
            } else {
                for (ArticleDto item : group) {
                    EvidenceChunkView chunk = toChunkView(item.content());
                    views.add(new EvidenceView(
                            item.title(),
                            item.source(),
                            item.publishedAt(),
                            item.url(),
                            List.of(chunk),
                            false,
                            1
                    ));
                }
            }
        }

        return views;
    }

    private EvidenceChunkView toChunkView(String content) {
        String trimmed = content == null ? "" : content.trim();
        return new EvidenceChunkView(trimmed, needsToggle(trimmed));
    }

    private boolean needsToggle(String content) {
        return content != null && content.length() > EVIDENCE_TOGGLE_THRESHOLD;
    }

    private String groupKey(ArticleDto article) {
        if (article == null) {
            return "unknown";
        }
        if (article.articleId() != null) {
            return "id:" + article.articleId();
        }
        if (hasText(article.url())) {
            return "url:" + article.url();
        }
        String published = article.publishedAt() == null ? "" : article.publishedAt().toString();
        return "title:" + safe(article.title()) + "|source:" + safe(article.source()) + "|published:" + published;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private record EvidenceView(
            String title,
            String source,
            LocalDateTime publishedAt,
            String url,
            List<EvidenceChunkView> chunks,
            boolean grouped,
            int chunkCount
    ) {}

    private record EvidenceChunkView(
            String content,
            boolean showToggle
    ) {}
}
