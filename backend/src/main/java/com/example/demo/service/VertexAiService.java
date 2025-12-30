package com.example.demo.service;

import com.example.demo.entity.Article;
import com.example.demo.util.PromptLoader;
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

    // main factcheck
    public String askModel(String claim, List<Article> evidence) {
        try {
            log.info("VertexAiService.askModel() called, claim length={}", claim.length());

            if (evidence == null) {
                evidence = List.of();
            }
            log.info("Evidence size={}", evidence.size());

            String endpoint = authHelper.chatEndpoint();

            // Build a fact-check prompt by injecting the claim and condensed evidence
            String prompt = buildFactcheckPrompt(claim, evidence);

            log.debug("LLM prompt  >>>\n{}\n<<< end prompt", prompt);

            String requestBody = buildRequestBody(prompt);

            log.debug("LLM request body >>>\n{}\n<<< end request body", requestBody);

            HttpResponse<String> response =
                    vertexApiClient.postJson(endpoint, requestBody);

            log.info("Vertex response status={}", response.statusCode());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.debug("Vertex raw response body >>>\n{}\n<<< end raw response", response.body());
                return extractTextFromResponse(response.body());
            } else {
                return "Vertex AI error " + response.statusCode() + ": " + response.body();
            }

        } catch (Exception e) {
            log.error("Error calling Vertex AI", e);
            return "Error calling Vertex AI: " + e.getMessage();
        }
    }

    private String buildFactcheckPrompt(String claim, List<Article> evidence) {
        String template = promptLoader.loadPrompt("factcheck");

        String evidenceText = (evidence == null || evidence.isEmpty())
                ? "(no evidence found)"
                : evidence.stream()
                .map(a -> a.getTitle() + " | " + a.getContent())
                .collect(Collectors.joining("\n\n---\n\n"));

        return template
                .replace("{{CLAIM}}", claim)
                .replace("{{EVIDENCE}}", evidenceText);
    }

    // 2) Bias & limitations
    public String analyzeBias(String claim, List<Article> evidence, String verdict) {
        try {
            if (evidence == null) {
                evidence = List.of();
            }

            String endpoint = authHelper.chatEndpoint();
            // Bias prompt
            String prompt = buildBiasPrompt(claim, evidence, verdict);
            log.debug("Bias prompt (truncated)={}...",
                    prompt.substring(0, Math.min(prompt.length(), 500)));

            String requestBody = buildRequestBody(prompt);
            HttpResponse<String> response =
                    vertexApiClient.postJson(endpoint, requestBody);

            log.info("Vertex bias analysis status={}", response.statusCode());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return extractTextFromResponse(response.body());
            } else {
                return "Bias analysis error " + response.statusCode() + ": " + response.body();
            }

        } catch (Exception e) {
            log.error("Error calling Vertex AI for bias analysis", e);
            return "Error in bias analysis: " + e.getMessage();
        }
    }

    private String buildBiasPrompt(String claim, List<Article> evidence, String verdict) {
        String template = promptLoader.loadPrompt("bias");

        String evidenceText = (evidence == null || evidence.isEmpty())
                ? "(no evidence found)"
                : evidence.stream()
                .map(a -> a.getTitle() + " | " + a.getSource() + " | " + a.getContent())
                .collect(Collectors.joining("\n\n---\n\n"));

        return template
                .replace("{{CLAIM}}", claim)
                .replace("{{EVIDENCE}}", evidenceText)
                .replace("{{VERDICT}}", verdict == null ? "unclear" : verdict);
    }

    // 3) Follow-up answers
    public String answerFollowUp(String claim,
                                 List<Article> evidence,
                                 String verdict,
                                 String explanation,
                                 String followupQuestion) {
        try {
            if (evidence == null) {
                evidence = List.of();
            }

            String endpoint = authHelper.chatEndpoint();
            // Follow-up prompt
            String prompt = buildFollowupPrompt(claim, evidence, verdict, explanation, followupQuestion);
            log.debug("Follow-up prompt (truncated)={}...",
                    prompt.substring(0, Math.min(prompt.length(), 500)));

            String requestBody = buildRequestBody(prompt);
            HttpResponse<String> response =
                    vertexApiClient.postJson(endpoint, requestBody);

            log.info("Vertex follow-up status={}", response.statusCode());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return extractTextFromResponse(response.body());
            } else {
                return "Follow-up error " + response.statusCode() + ": " + response.body();
            }

        } catch (Exception e) {
            log.error("Error calling Vertex AI for follow-up", e);
            return "Error in follow-up answer: " + e.getMessage();
        }
    }

    private String buildFollowupPrompt(String claim,
                                       List<Article> evidence,
                                       String verdict,
                                       String explanation,
                                       String followupQuestion) {
        String template = promptLoader.loadPrompt("followup");

        String evidenceText = (evidence == null || evidence.isEmpty())
                ? "(no evidence found)"
                : evidence.stream()
                .map(a -> a.getTitle() + " | " + a.getSource() + " | " + a.getContent())
                .collect(Collectors.joining("\n\n---\n\n"));

        return template
                .replace("{{CLAIM}}", claim)
                .replace("{{EVIDENCE}}", evidenceText)
                .replace("{{VERDICT}}", verdict == null ? "unclear" : verdict)
                .replace("{{EXPLANATION}}", explanation == null ? "(no explanation stored)" : explanation)
                .replace("{{FOLLOWUP_QUESTION}}", followupQuestion);
    }

    // Shared helpers
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
