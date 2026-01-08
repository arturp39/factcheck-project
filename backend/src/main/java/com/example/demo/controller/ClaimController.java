package com.example.demo.controller;

import com.example.demo.entity.Article;
import com.example.demo.entity.ClaimFollowup;
import com.example.demo.entity.ClaimLog;
import com.example.demo.service.ClaimWorkflowService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
public class ClaimController {

    private final ClaimWorkflowService claimWorkflowService;

    public ClaimController(ClaimWorkflowService claimWorkflowService) {
        this.claimWorkflowService = claimWorkflowService;
    }

    @PostMapping("/verify")
    public String verify(@RequestParam String claim, Model model) {
        try {
            ClaimWorkflowService.VerifyResult result = claimWorkflowService.verify(claim, null);
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
            model.addAttribute("recentClaims", claimWorkflowService.listRecentClaims(10));
            return "index";
        }
    }

    @PostMapping("/followup/{id}")
    public String followup(@PathVariable("id") Long claimId,
                           @RequestParam("question") String question,
                           Model model) {

        String normalizedQ = question == null ? "" : question.trim();
        if (normalizedQ.isEmpty()) {
            ClaimWorkflowService.ClaimContext context =
                    claimWorkflowService.loadClaimContext(claimId, null);
            model.addAttribute("error", "Follow-up question must not be empty.");
            applyBaseModel(
                    model,
                    context.claimId(),
                    context.claim(),
                    context.evidence(),
                    context.verdict(),
                    context.explanation(),
                    context.biasAnalysis(),
                    claimWorkflowService.listFollowups(claimId)
            );
            return "result";
        }

        ClaimWorkflowService.FollowupResult result =
                claimWorkflowService.followup(claimId, normalizedQ, null);
        applyBaseModel(
                model,
                result.claimId(),
                result.claim(),
                result.evidence(),
                result.verdict(),
                result.explanation(),
                result.biasAnalysis(),
                claimWorkflowService.listFollowups(claimId)
        );
        model.addAttribute("followupQuestion", result.question());
        model.addAttribute("followupAnswer", result.answer());

        return "result";
    }

    @PostMapping("/bias/{id}")
    public String analyzeBias(@PathVariable("id") Long claimId, Model model) {

        ClaimWorkflowService.BiasResult result = claimWorkflowService.bias(claimId, null);
        applyBaseModel(
                model,
                result.claimId(),
                result.claim(),
                result.evidence(),
                result.verdict(),
                null,
                result.biasAnalysis(),
                claimWorkflowService.listFollowups(claimId)
        );

        return "result";
    }

    @GetMapping("/history/{id}")
    public String history(@PathVariable("id") Long claimId, Model model) {
        ClaimWorkflowService.ConversationHistory history =
                claimWorkflowService.loadConversationHistory(claimId, null);
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
        List<ClaimLog> recentClaims = claimWorkflowService.listRecentClaims(10);
        model.addAttribute("recentClaims", recentClaims);
        return "index";
    }

    private void applyBaseModel(
            Model model,
            Long claimId,
            String claim,
            List<Article> evidence,
            String verdict,
            String explanation,
            String biasAnalysis,
            List<ClaimFollowup> followups
    ) {
        model.addAttribute("claimId", claimId);
        model.addAttribute("claim", claim);
        model.addAttribute("evidence", evidence);
        model.addAttribute("verdict", verdict);
        model.addAttribute("explanation", explanation);
        model.addAttribute("biasAnalysis", biasAnalysis);
        model.addAttribute("followups", followups == null ? List.of() : followups);
    }
}
