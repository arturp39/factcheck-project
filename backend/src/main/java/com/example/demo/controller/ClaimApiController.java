package com.example.demo.controller;

import com.example.demo.api.ClaimApiModels.*;
import com.example.demo.service.ClaimApiService;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/claims")
public class ClaimApiController {

    private final ClaimApiService claimApiService;

    public ClaimApiController(
            ClaimApiService claimApiService,
            @Value("${app.claim.max-length:400}") int claimMaxLength
    ) {
        this.claimApiService = claimApiService;
    }

    @PostMapping("/verify")
    public ResponseEntity<VerifyResponse> verify(@RequestBody VerifyRequest request) {
        String correlationId = getCorrelationId();
        String claim = request != null ? request.claim() : null;
        return ResponseEntity.ok(claimApiService.verify(claim, correlationId));
    }

    @GetMapping
    public ResponseEntity<ClaimsPageResponse> listClaims(
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "20") int size
    ) {
        String correlationId = getCorrelationId();
        return ResponseEntity.ok(claimApiService.listClaims(page, size, correlationId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClaimResponse> getClaim(@PathVariable("id") Long claimId) {
        String correlationId = getCorrelationId();
        return ResponseEntity.ok(claimApiService.getClaim(claimId, correlationId));
    }

    @GetMapping("/{id}/evidence")
    public ResponseEntity<EvidenceResponse> getEvidence(@PathVariable("id") Long claimId) {
        String correlationId = getCorrelationId();
        return ResponseEntity.ok(claimApiService.getEvidence(claimId, correlationId));
    }

    @PostMapping("/{id}/followup")
    public ResponseEntity<FollowupResponse> followup(
            @PathVariable("id") Long claimId,
            @RequestBody FollowupRequest request
    ) {
        String correlationId = getCorrelationId();
        String question = request != null ? request.question() : null;
        return ResponseEntity.ok(claimApiService.followup(claimId, question, correlationId));
    }

    @PostMapping("/{id}/bias")
    public ResponseEntity<BiasResponse> bias(@PathVariable("id") Long claimId) {
        String correlationId = getCorrelationId();
        return ResponseEntity.ok(claimApiService.bias(claimId, correlationId));
    }

    private String getCorrelationId() {
        String cid = MDC.get("corrId");
        return (cid != null && !cid.isBlank()) ? cid : UUID.randomUUID().toString();
    }
}
