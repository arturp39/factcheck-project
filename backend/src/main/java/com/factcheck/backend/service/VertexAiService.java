package com.factcheck.backend.service;

import com.factcheck.backend.dto.ArticleDto;
import com.factcheck.backend.util.PromptLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class VertexAiService {

    private final VertexAuthHelper authHelper;
    private final VertexApiClient vertexApiClient;
    private final ObjectMapper mapper;
    private final PromptLoader promptLoader;

    public VertexAiService(VertexAuthHelper authHelper,
                           VertexApiClient vertexApiClient,
                           PromptLoader promptLoader) {
        this.authHelper = authHelper;
        this.vertexApiClient = vertexApiClient;
        this.promptLoader = promptLoader;
        this.mapper = new ObjectMapper();
    }

    public String askModel(String claim, List<ArticleDto> evidence) {
        try {
            if (evidence == null) {
                evidence = List.of();
            }

            String endpoint = authHelper.chatEndpoint();
            String prompt = buildFactcheckPrompt(claim, evidence);
            String requestBody = buildRequestBody(prompt);

            HttpResponse<String> response = vertexApiClient.postJson(endpoint, requestBody);

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return extractTextFromResponse(response.body());
            }
            return "Vertex AI error " + response.statusCode() + ": " + response.body();

        } catch (Exception e) {
            log.error("Error calling Vertex AI", e);
            return "Error calling Vertex AI: " + e.getMessage();
        }
    }

    private String buildFactcheckPrompt(String claim, List<ArticleDto> evidence) {
        String template = promptLoader.loadPrompt("factcheck");

        String evidenceText = (evidence == null || evidence.isEmpty())
                ? "(no evidence found)"
                : evidence.stream()
                .map(this::formatEvidenceForFactcheck)
                .collect(Collectors.joining("\n\n---\n\n"));

        return template
                .replace("{{CLAIM}}", claim)
                .replace("{{EVIDENCE}}", evidenceText);
    }

    public String analyzeBias(String claim, List<ArticleDto> evidence, String verdict) {
        try {
            if (evidence == null) {
                evidence = List.of();
            }

            String endpoint = authHelper.chatEndpoint();
            String prompt = buildBiasPrompt(claim, evidence, verdict);
            String requestBody = buildRequestBody(prompt);

            HttpResponse<String> response = vertexApiClient.postJson(endpoint, requestBody);

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return extractTextFromResponse(response.body());
            }
            return "Bias analysis error " + response.statusCode() + ": " + response.body();

        } catch (Exception e) {
            log.error("Error calling Vertex AI for bias analysis", e);
            return "Error in bias analysis: " + e.getMessage();
        }
    }

    private String buildBiasPrompt(String claim, List<ArticleDto> evidence, String verdict) {
        String template = promptLoader.loadPrompt("bias");

        String evidenceText = (evidence == null || evidence.isEmpty())
                ? "(no evidence found)"
                : evidence.stream()
                .map(this::formatEvidenceForBias)
                .collect(Collectors.joining("\n\n---\n\n"));

        return template
                .replace("{{CLAIM}}", claim)
                .replace("{{EVIDENCE}}", evidenceText)
                .replace("{{VERDICT}}", verdict == null ? "unclear" : verdict);
    }

    public String answerFollowUp(String claim,
                                 List<ArticleDto> evidence,
                                 String verdict,
                                 String explanation,
                                 String followupQuestion) {
        try {
            if (evidence == null) {
                evidence = List.of();
            }

            String endpoint = authHelper.chatEndpoint();
            String prompt = buildFollowupPrompt(claim, evidence, verdict, explanation, followupQuestion);
            String requestBody = buildRequestBody(prompt);

            HttpResponse<String> response = vertexApiClient.postJson(endpoint, requestBody);

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return extractTextFromResponse(response.body());
            }
            return "Follow-up error " + response.statusCode() + ": " + response.body();

        } catch (Exception e) {
            log.error("Error calling Vertex AI for follow-up", e);
            return "Error in follow-up answer: " + e.getMessage();
        }
    }

    private String buildFollowupPrompt(String claim,
                                       List<ArticleDto> evidence,
                                       String verdict,
                                       String explanation,
                                       String followupQuestion) {
        String template = promptLoader.loadPrompt("followup");

        String evidenceText = (evidence == null || evidence.isEmpty())
                ? "(no evidence found)"
                : evidence.stream()
                .map(a -> a.title() + " | " + a.source() + " | " + a.content())
                .collect(Collectors.joining("\n\n---\n\n"));

        return template
                .replace("{{CLAIM}}", claim)
                .replace("{{EVIDENCE}}", evidenceText)
                .replace("{{VERDICT}}", verdict == null ? "unclear" : verdict)
                .replace("{{EXPLANATION}}", explanation == null ? "(no explanation stored)" : explanation)
                .replace("{{FOLLOWUP_QUESTION}}", followupQuestion);
    }

    private String formatEvidenceForFactcheck(ArticleDto article) {
        String mbfc = buildMbfcInfo(article, false);
        if (mbfc == null) {
            return article.title() + " | " + article.content();
        }
        return article.title() + " | " + mbfc + " | " + article.content();
    }

    private String formatEvidenceForBias(ArticleDto article) {
        String mbfc = buildMbfcInfo(article, true);
        if (mbfc == null) {
            return article.title() + " | " + article.source() + " | " + article.content();
        }
        return article.title() + " | " + article.source() + " | " + mbfc + " | " + article.content();
    }

    private String buildMbfcInfo(ArticleDto article, boolean includeBias) {
        List<String> parts = new java.util.ArrayList<>(3);
        if (includeBias && isNotBlank(article.mbfcBias())) {
            parts.add("bias=" + article.mbfcBias());
        }
        if (isNotBlank(article.mbfcFactualReporting())) {
            parts.add("factual_reporting=" + article.mbfcFactualReporting());
        }
        if (isNotBlank(article.mbfcCredibility())) {
            parts.add("credibility=" + article.mbfcCredibility());
        }
        if (parts.isEmpty()) {
            return null;
        }
        return "MBFC(" + String.join(", ", parts) + ")";
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String buildRequestBody(String prompt) throws Exception {
        var root = mapper.createObjectNode();
        var contents = root.putArray("contents");

        var userContent = contents.addObject();
        userContent.put("role", "user");
        var parts = userContent.putArray("parts");
        var part = parts.addObject();
        part.put("text", prompt);

        return mapper.writeValueAsString(root);
    }

    private String extractTextFromResponse(String body) throws Exception {
        JsonNode root = mapper.readTree(body);
        JsonNode candidates = root.path("candidates");
        if (candidates.isArray() && !candidates.isEmpty()) {
            JsonNode parts = candidates.get(0)
                    .path("content")
                    .path("parts");
            if (parts.isArray() && !parts.isEmpty()) {
                JsonNode textNode = parts.get(0).path("text");
                if (!textNode.isMissingNode()) {
                    return textNode.asText();
                }
            }
        }
        return "No text field found in AI response: " + body;
    }
}