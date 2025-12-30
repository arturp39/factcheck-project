package com.example.demo.service;

import com.example.demo.entity.Article;
import com.example.demo.entity.ClaimLog;
import com.example.demo.integration.nlp.NlpServiceClient;
import com.example.demo.repository.ClaimLogRepository;
import com.example.demo.service.WeaviateClientService.EvidenceChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ClaimService {

    private final ClaimLogRepository claimRepo;
    private final NlpServiceClient nlpServiceClient;
    private final WeaviateClientService weaviateClientService;
    private final int searchTopK;

    public ClaimService(ClaimLogRepository claimRepo,
                        NlpServiceClient nlpServiceClient,
                        WeaviateClientService weaviateClientService,
                        @Value("${app.search.top-k:5}") int searchTopK) {
        this.claimRepo = claimRepo;
        this.nlpServiceClient = nlpServiceClient;
        this.weaviateClientService = weaviateClientService;
        this.searchTopK = searchTopK;
    }

    public List<Article> searchEvidence(String claim) {
        return searchEvidence(claim, null);
    }

    public List<Article> searchEvidence(String claim, String correlationId) {
        log.info("searchEvidence() called with claim='{}'", claim);

        try {
            String cid = (correlationId != null && !correlationId.isBlank())
                    ? correlationId
                    : UUID.randomUUID().toString();
            log.debug("Using correlationId={} for claim", cid);

            // Embed the claim once so Weaviate vector search can be used
            float[] claimVector = nlpServiceClient.embedSingleToVector(claim, cid);
            log.info("Claim embedding length={}", claimVector.length);

            // Send the vector to Weaviate and parse the returned chunks
            String graphqlResponse = weaviateClientService.searchByVector(claimVector, searchTopK);
            List<EvidenceChunk> chunks =
                    weaviateClientService.parseEvidenceChunks(graphqlResponse);

            log.info("Weaviate returned {} evidence chunks", chunks.size());

            // Map raw chunks into Article objects expected by the UI
            return chunks.stream()
                    .map(c -> {
                        Article a = new Article();
                        a.setTitle(c.title());
                        a.setContent(c.content());
                        a.setSource(c.source());
                        return a;
                    })
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Vector search failed for claim='{}'", claim, e);
            throw new RuntimeException("Vector search failed: " + e.getMessage(), e);
        }
    }

    public ClaimLog saveClaim(String claim) {
        ClaimLog logEntity = new ClaimLog();
        logEntity.setClaimText(claim);
        ClaimLog saved = claimRepo.save(logEntity);
        log.info("Saved claim id={} text='{}'", saved.getId(), claim);
        return saved;
    }

    public ParsedAnswer storeModelAnswer(Long claimId, String answer) {
        log.info("Storing model answer for claimId={}", claimId);

        ClaimLog logEntity = getClaim(claimId);
        ParsedAnswer parsed = parseAnswer(answer);

        logEntity.setModelAnswer(parsed.rawAnswer());
        logEntity.setVerdict(parsed.verdict());
        logEntity.setExplanation(parsed.explanation());
        claimRepo.save(logEntity);

        return parsed;
    }

    public void storeBiasAnalysis(Long claimId, String biasText) {
        log.info("Storing bias analysis for claimId={}", claimId);
        ClaimLog logEntity = getClaim(claimId);
        logEntity.setBiasAnalysis(biasText);
        claimRepo.save(logEntity);
    }

    public ClaimLog getClaim(Long id) {
        return claimRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + id));
    }

    public Page<ClaimLog> listClaims(Pageable pageable) {
        return claimRepo.findAll(pageable);
    }

    private ParsedAnswer parseAnswer(String answer) {
        if (answer == null) {
            return new ParsedAnswer("unclear", "(no explanation)", null);
        }

        String trimmed = answer.trim();
        String verdict = "unclear";

        String[] lines = trimmed.split("\\R");
        for (String line : lines) {
            String l = line.trim();
            if (l.toLowerCase().startsWith("verdict:")) {
                String v = l.substring("verdict:".length()).trim().toLowerCase();
                if (v.startsWith("true")) verdict = "true";
                else if (v.startsWith("false")) verdict = "false";
                else if (v.startsWith("mixed")) verdict = "mixed";
                else verdict = "unclear";
                break;
            }
        }

        String explanation = trimmed
                .replaceFirst("(?is)^\\s*verdict\\s*:[^\\n\\r]*[\\n\\r]*", "")
                .trim();
        if (explanation.isBlank()) {
            explanation = "(no explanation)";
        }

        return new ParsedAnswer(verdict, explanation, answer);
    }

    public record ParsedAnswer(
            String verdict,
            String explanation,
            String rawAnswer
    ) {}
}
