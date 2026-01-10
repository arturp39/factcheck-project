package com.factcheck.backend.service;

import com.factcheck.backend.config.WeaviateProperties;
import com.factcheck.backend.exception.WeaviateException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeaviateClientService {

    public static final String CORRELATION_HEADER = "X-Correlation-Id";

    private final WeaviateProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public record EvidenceChunk(
            String title,
            String content,
            String source,
            LocalDateTime publishedAt,
            String mbfcBias,
            String mbfcFactualReporting,
            String mbfcCredibility
    ) {
    }

    private HttpRequest.Builder requestBuilder(String path, String correlationId) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(props.getBaseUrl() + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json");

        builder.header(CORRELATION_HEADER, ensureCorrelationId(correlationId));

        String apiKey = props.getApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("X-API-KEY", apiKey);
        }
        return builder;
    }

    public String insertArticleChunk(String title,
                                     String content,
                                     String source,
                                     float[] vector) {
        return insertArticleChunk(title, content, source, vector, null);
    }

    public String insertArticleChunk(String title,
                                     String content,
                                     String source,
                                     float[] vector,
                                     String correlationId) {
        try {
            if (vector == null || vector.length == 0) {
                throw new WeaviateException("Vector must not be empty");
            }
            // safe fallback source so Weaviate objects are labeled even when caller omits it
            String safeSource = (source == null || source.isBlank()) ? "manual" : source;
            String requestBody = buildInsertBody(title, content, safeSource, vector);

            HttpRequest request = requestBuilder("/v1/objects", correlationId)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> resp =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.error("Weaviate insertArticleChunk failed status={} body={}",
                        resp.statusCode(), resp.body());
                throw new WeaviateException("Weaviate insert HTTP " + resp.statusCode());
            }

            log.info("Inserted manual article chunk title={} source={} status={}",
                    title, safeSource, resp.statusCode());
            return resp.body();

        } catch (Exception e) {
            if (e instanceof WeaviateException we) {
                throw we;
            }
            throw new WeaviateException("Failed to insert article chunk into Weaviate", e);
        }
    }

    public String searchByVector(float[] vector, int limit) {
        return searchByVector(vector, limit, null);
    }

    public String searchByVector(float[] vector, int limit, String correlationId) {
        try {
            String gql = buildQuery(vector, limit);

            ObjectNode root = mapper.createObjectNode();
            root.put("query", gql);
            String body = mapper.writeValueAsString(root);

            HttpRequest request = requestBuilder("/v1/graphql", correlationId)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.error("Weaviate searchByVector failed status={} body={}",
                        resp.statusCode(), resp.body());
                throw new WeaviateException("Weaviate GraphQL HTTP " + resp.statusCode());
            }

            log.debug("Weaviate searchByVector response={}", resp.body());
            return resp.body();
        } catch (Exception e) {
            if (e instanceof WeaviateException we) {
                throw we;
            }
            throw new WeaviateException("Weaviate search failed", e);
        }
    }

    /**
     * Build GraphQL query using the collector's ArticleChunk schema.
     * request these fields:
     * - text
     * - articleTitle
     * - sourceName
     * - publishedDate
     * - _additional { distance }
     */
    private String buildQuery(float[] vector, int limit) {
        StringBuilder vecBuilder = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) vecBuilder.append(",");
            vecBuilder.append(vector[i]);
        }
        vecBuilder.append("]");

        // GraphQL matches collector schema so only needed fields are returned
        return String.format(
                java.util.Locale.US,
                "{ Get { ArticleChunk(" +
                        "nearVector: { vector: %s, distance: %f }, limit: %d" +
                        ") {" +
                        " text" +
                        " articleTitle" +
                        " sourceName" +
                        " publishedDate" +
                        " mbfcBias" +
                        " mbfcFactualReporting" +
                        " mbfcCredibility" +
                        " _additional { distance }" +
                        " } } }",
                vecBuilder,
                props.getMaxDistance(),
                limit
        );
    }

    private String buildInsertBody(String title,
                                   String content,
                                   String source,
                                   float[] vector) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("class", "ArticleChunk");

        ObjectNode props = root.putObject("properties");
        props.put("text", content);
        props.put("articleTitle", title);
        props.put("sourceName", source);
        props.put("articleId", 0);
        props.put("articleUrl", "");
        props.put("publishedDate", java.time.Instant.now().toString());
        props.put("chunkIndex", 0);

        var vectorNode = root.putArray("vector");
        for (float v : vector) {
            vectorNode.add(v);
        }
        return mapper.writeValueAsString(root);
    }

    public List<EvidenceChunk> parseEvidenceChunks(String graphqlResponse) {
        try {
            JsonNode root = mapper.readTree(graphqlResponse);

            JsonNode errors = root.path("errors");
            if (errors.isArray() && !errors.isEmpty()) {
                log.error("Weaviate GraphQL errors={}", errors);
                throw new WeaviateException("Weaviate GraphQL returned errors");
            }

            JsonNode data = root.path("data").path("Get").path("ArticleChunk");
            List<EvidenceChunk> chunks = new ArrayList<>();

            if (data.isArray()) {
                for (JsonNode n : data) {
                    JsonNode distNode = n.path("_additional").path("distance");
                    float distance = distNode.isMissingNode() ? 1.0f : (float) distNode.asDouble();

                    if (distance > props.getMaxDistance()) {
                        continue;
                    }

                    // Extract only the fields for ui
                    String title = n.path("articleTitle").asText("");
                    String content = n.path("text").asText("");
                    String source = n.path("sourceName").asText("");
                    String publishedIso = n.path("publishedDate").asText(null);
                    LocalDateTime publishedAt = null;
                    if (publishedIso != null && !publishedIso.isBlank()) {
                        try {
                            Instant inst = Instant.parse(publishedIso);
                            publishedAt = LocalDateTime.ofInstant(inst, ZoneOffset.UTC);
                        } catch (Exception ignored) {
                            publishedAt = null;
                        }
                    }
                    String mbfcBias = nullIfBlank(n.path("mbfcBias").asText(null));
                    String mbfcFactualReporting = nullIfBlank(n.path("mbfcFactualReporting").asText(null));
                    String mbfcCredibility = nullIfBlank(n.path("mbfcCredibility").asText(null));

                    chunks.add(new EvidenceChunk(
                            title,
                            content,
                            source,
                            publishedAt,
                            mbfcBias,
                            mbfcFactualReporting,
                            mbfcCredibility
                    ));
                }
            } else {
                log.warn("No ArticleChunk array in Weaviate response");
            }

            log.info("parseEvidenceChunks() extracted {} chunks", chunks.size());
            return chunks;
        } catch (Exception e) {
            if (e instanceof WeaviateException we) {
                throw we;
            }
            throw new WeaviateException("Weaviate parse failed", e);
        }
    }

    private String nullIfBlank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String ensureCorrelationId(String correlationId) {
        if (correlationId != null && !correlationId.isBlank()) {
            return correlationId;
        }
        return UUID.randomUUID().toString();
    }
}
