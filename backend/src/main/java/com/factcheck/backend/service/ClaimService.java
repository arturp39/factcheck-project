package com.factcheck.backend.service;

import com.factcheck.backend.dto.ArticleDto;
import com.factcheck.backend.entity.ClaimFollowup;
import com.factcheck.backend.entity.ClaimLog;
import com.factcheck.backend.exception.EvidenceSearchException;
import com.factcheck.backend.exception.NlpServiceException;
import com.factcheck.backend.exception.WeaviateException;
import com.factcheck.backend.integration.nlp.NlpServiceClient;
import com.factcheck.backend.repository.ClaimFollowupRepository;
import com.factcheck.backend.repository.ClaimLogRepository;
import com.factcheck.backend.service.WeaviateClientService.EvidenceChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class ClaimService {

    private final ClaimLogRepository claimRepo;
    private final ClaimFollowupRepository followupRepo;
    private final NlpServiceClient nlpServiceClient;
    private final WeaviateClientService weaviateClientService;
    private final int searchTopK;
    private static final Set<String> BAD_BIAS_TOKENS = Set.of(
            "questionable",
            "conspiracy",
            "pseudoscience",
            "satire"
    );
    private static final Set<String> BAD_FACTUAL_VALUES = Set.of(
            "very low",
            "low"
    );

    public ClaimService(ClaimLogRepository claimRepo,
                        ClaimFollowupRepository followupRepo,
                        NlpServiceClient nlpServiceClient,
                        WeaviateClientService weaviateClientService,
                        @Value("${app.search.top-k:5}") int searchTopK) {
        this.claimRepo = claimRepo;
        this.followupRepo = followupRepo;
        this.nlpServiceClient = nlpServiceClient;
        this.weaviateClientService = weaviateClientService;
        this.searchTopK = searchTopK;
    }

    public List<ArticleDto> searchEvidence(String claim) {
        return searchEvidence(claim, null);
    }

    public List<ArticleDto> searchEvidence(String claim, String correlationId) {
        log.info("searchEvidence() called with claim='{}'", claim);

        try {
            String cid = (correlationId != null && !correlationId.isBlank())
                    ? correlationId
                    : UUID.randomUUID().toString();

            float[] claimVector = nlpServiceClient.embedSingleToVector(claim, cid);

            String graphqlResponse = weaviateClientService.searchByVector(claimVector, searchTopK, cid);
            List<EvidenceChunk> chunks = weaviateClientService.parseEvidenceChunks(graphqlResponse);

            return chunks.stream()
                    .filter(chunk -> !isBadSource(chunk))
                    .map(c -> new ArticleDto(
                            c.articleId(),
                            c.title(),
                            c.content(),
                            c.source(),
                            c.publishedAt(),
                            c.articleUrl(),
                            c.mbfcBias(),
                            c.mbfcFactualReporting(),
                            c.mbfcCredibility()
                    ))
                    .toList();

        } catch (NlpServiceException | WeaviateException | EvidenceSearchException e) {
            log.error("Vector search failed for claim='{}'", claim, e);
            throw e;
        } catch (Exception e) {
            log.error("Vector search failed for claim='{}'", claim, e);
            throw new EvidenceSearchException("Vector search failed: " + e.getMessage(), e);
        }
    }

    public ClaimLog saveClaim(String claim, String ownerUsername) {
        String owner = requireOwner(ownerUsername);
        ClaimLog logEntity = new ClaimLog();
        logEntity.setClaimText(claim);
        logEntity.setOwnerUsername(owner);
        return claimRepo.save(logEntity);
    }

    public ParsedAnswer storeModelAnswer(Long claimId, String answer, String ownerUsername, boolean allowAdmin) {
        ClaimLog logEntity = getClaim(claimId, ownerUsername, allowAdmin);
        ParsedAnswer parsed = parseAnswer(answer);

        logEntity.setModelAnswer(parsed.rawAnswer());
        logEntity.setVerdict(parsed.verdict());
        logEntity.setExplanation(parsed.explanation());
        claimRepo.save(logEntity);

        return parsed;
    }

    public void storeBiasAnalysis(Long claimId, String biasText, String ownerUsername, boolean allowAdmin) {
        ClaimLog logEntity = getClaim(claimId, ownerUsername, allowAdmin);
        logEntity.setBiasAnalysis(biasText);
        claimRepo.save(logEntity);
    }

    public ClaimFollowup storeFollowup(Long claimId, String question, String answer, String ownerUsername, boolean allowAdmin) {
        ClaimLog logEntity = getClaim(claimId, ownerUsername, allowAdmin);
        ClaimFollowup followup = new ClaimFollowup();
        followup.setClaim(logEntity);
        followup.setQuestion(question);
        followup.setAnswer(answer);
        return followupRepo.save(followup);
    }

    public List<ClaimFollowup> listFollowups(Long claimId, String ownerUsername, boolean allowAdmin) {
        getClaim(claimId, ownerUsername, allowAdmin);
        return followupRepo.findByClaimIdOrderByCreatedAtAsc(claimId);
    }

    public ClaimLog getClaim(Long id, String ownerUsername, boolean allowAdmin) {
        ClaimLog log = claimRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + id));
        if (!allowAdmin) {
            String owner = requireOwner(ownerUsername);
            if (!owner.equals(log.getOwnerUsername())) {
                throw new AccessDeniedException("Claim does not belong to the current user.");
            }
        }
        return log;
    }

    public Page<ClaimLog> listClaims(Pageable pageable, String ownerUsername, boolean allowAdmin) {
        if (allowAdmin) {
            return claimRepo.findAll(pageable);
        }
        String owner = requireOwner(ownerUsername);
        return claimRepo.findByOwnerUsername(owner, pageable);
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

    private boolean isBadSource(EvidenceChunk chunk) {
        if (chunk == null) {
            return false;
        }
        return isBadBias(chunk.mbfcBias())
                || isBadFactual(chunk.mbfcFactualReporting())
                || isBadFactual(chunk.mbfcCredibility());
    }

    private boolean isBadBias(String bias) {
        if (bias == null || bias.isBlank()) {
            return false;
        }
        String normalized = normalizeLabel(bias);
        for (String token : BAD_BIAS_TOKENS) {
            if (normalized.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBadFactual(String label) {
        if (label == null || label.isBlank()) {
            return false;
        }
        String normalized = normalizeLabel(label);
        return BAD_FACTUAL_VALUES.contains(normalized);
    }

    private String normalizeLabel(String value) {
        String normalized = value.toLowerCase()
                .replace('_', ' ')
                .replace('-', ' ')
                .replace('/', ' ')
                .trim();
        return normalized.replaceAll("\\s+", " ");
    }

    private String requireOwner(String ownerUsername) {
        if (ownerUsername == null || ownerUsername.isBlank()) {
            throw new IllegalArgumentException("Owner username must be provided.");
        }
        return ownerUsername;
    }
}
