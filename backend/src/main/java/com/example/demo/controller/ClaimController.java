package com.example.demo.controller;

import com.example.demo.entity.Article;
import com.example.demo.entity.ClaimLog;
import com.example.demo.service.ClaimService;
import com.example.demo.service.VertexAiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@Slf4j
public class ClaimController {

    private final ClaimService claimService;
    private final VertexAiService vertexService;
    private final int claimMaxLength;

    public ClaimController(ClaimService claimService,
                           VertexAiService vertexService,
                           @Value("${app.claim.max-length:400}") int claimMaxLength) {
        this.claimService = claimService;
        this.vertexService = vertexService;
        this.claimMaxLength = claimMaxLength;
    }

    @PostMapping("/verify")
    public String verify(@RequestParam String claim, Model model) {
        String normalized = claim == null ? "" : claim.trim();

        if (normalized.isEmpty()) {
            model.addAttribute("error", "Claim must not be empty.");
            return "index";
        }

        if (normalized.length() > claimMaxLength) {
            model.addAttribute("error", "Claim is too long. Please keep it under " + claimMaxLength + " characters.");
            return "index";
        }

        log.info("verify called claimLength={}", normalized.length());

        // Save claim log
        ClaimLog saved = claimService.saveClaim(normalized);

        // Search evidence in Weaviate
        List<Article> evidence = claimService.searchEvidence(normalized);
        log.info("verify evidenceSize={}", evidence.size());

        // Main fact-check
        String aiResponse = vertexService.askModel(normalized, evidence);

        // Parse + store verdict and explanation
        ClaimService.ParsedAnswer parsed = claimService.storeModelAnswer(saved.getId(), aiResponse);

        // Model attributes for result page
        model.addAttribute("claimId", saved.getId());
        model.addAttribute("claim", normalized);
        model.addAttribute("evidence", evidence);
        model.addAttribute("verdict", parsed.verdict());
        model.addAttribute("explanation", parsed.explanation());

        return "result";
    }

    @PostMapping("/followup/{id}")
    public String followup(@PathVariable("id") Long claimId,
                           @RequestParam("question") String question,
                           Model model) {

        String normalizedQ = question == null ? "" : question.trim();
        ClaimLog log = claimService.getClaim(claimId);

        if (normalizedQ.isEmpty()) {
            model.addAttribute("error", "Follow-up question must not be empty.");
        }

        // fetch evidence again based on original claim
        List<Article> evidence = claimService.searchEvidence(log.getClaimText());

        // base context
        model.addAttribute("claimId", claimId);
        model.addAttribute("claim", log.getClaimText());
        model.addAttribute("evidence", evidence);
        model.addAttribute("verdict", log.getVerdict());
        model.addAttribute("explanation", log.getExplanation());
        model.addAttribute("biasAnalysis", log.getBiasAnalysis());

        if (normalizedQ.isEmpty()) {
            return "result";
        }

        String answer = vertexService.answerFollowUp(
                log.getClaimText(),
                evidence,
                log.getVerdict(),
                log.getExplanation(),
                normalizedQ
        );

        model.addAttribute("followupQuestion", normalizedQ);
        model.addAttribute("followupAnswer", answer);

        return "result";
    }

    @PostMapping("/bias/{id}")
    public String analyzeBias(@PathVariable("id") Long claimId, Model model) {

        ClaimLog log = claimService.getClaim(claimId);

        // fetch evidence
        List<Article> evidence = claimService.searchEvidence(log.getClaimText());

        // call bias prompt
        String biasText = vertexService.analyzeBias(
                log.getClaimText(),
                evidence,
                log.getVerdict()
        );

        claimService.storeBiasAnalysis(claimId, biasText);

        // rebuild model
        model.addAttribute("claimId", claimId);
        model.addAttribute("claim", log.getClaimText());
        model.addAttribute("evidence", evidence);
        model.addAttribute("verdict", log.getVerdict());
        model.addAttribute("explanation", log.getExplanation());
        model.addAttribute("biasAnalysis", biasText);

        return "result";
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }
}