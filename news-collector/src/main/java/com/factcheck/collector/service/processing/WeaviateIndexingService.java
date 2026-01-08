package com.factcheck.collector.service.processing;

import com.factcheck.collector.domain.entity.Article;
import com.factcheck.collector.dto.ChunkResult;
import com.factcheck.collector.exception.WeaviateException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeaviateIndexingService {

    public static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String CLASS_NAME = "ArticleChunk";

    private final ObjectMapper mapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @Value("${weaviate.base-url}")
    private String baseUrl;

    @Value("${weaviate.article-chunk-limit:512}")
    private int articleChunkLimit;

    @Value("${weaviate.http-timeout:PT20S}")
    private Duration httpTimeout;

    public void ensureSchema() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/schema"))
                    .timeout(httpTimeout)
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new WeaviateException("Schema fetch failed HTTP " + resp.statusCode() + " body=" + safeBody(resp.body()), null);
            }

            JsonNode root = mapper.readTree(resp.body());
            JsonNode classes = root.path("classes");

            boolean hasClass = false;
            Set<String> existingProps = new HashSet<>();
            if (classes.isArray()) {
                for (JsonNode c : classes) {
                    if (CLASS_NAME.equalsIgnoreCase(c.path("class").asText())) {
                        hasClass = true;
                        JsonNode props = c.path("properties");
                        if (props.isArray()) {
                            for (JsonNode p : props) {
                                String name = p.path("name").asText(null);
                                if (name != null && !name.isBlank()) {
                                    existingProps.add(name);
                                }
                            }
                        }
                        break;
                    }
                }
            }

            if (hasClass) {
                ensureMbfcProperties(existingProps);
                return;
            }

            log.info("Creating Weaviate class {}", CLASS_NAME);

            String body = """
                    {
                      "class": "ArticleChunk",
                      "description": "Small article fragment for fact-checking",
                      "vectorizer": "none",
                      "properties": [
                        { "name": "text",         "dataType": ["text"] },
                        { "name": "articleId",    "dataType": ["int"] },
                        { "name": "articleUrl",   "dataType": ["text"] },
                        { "name": "articleTitle", "dataType": ["text"] },
                        { "name": "sourceName",   "dataType": ["text"] },
                        { "name": "mbfcBias",     "dataType": ["text"] },
                        { "name": "mbfcFactualReporting", "dataType": ["text"] },
                        { "name": "mbfcCredibility", "dataType": ["text"] },
                        { "name": "publishedDate","dataType": ["date"] },
                        { "name": "chunkIndex",   "dataType": ["int"] }
                      ]
                    }
                    """;

            HttpRequest createReq = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/schema"))
                    .timeout(httpTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> createResp = httpClient.send(createReq, HttpResponse.BodyHandlers.ofString());

            // Treat 422/409 "already exists" as success to handle schema creation races.
            if (createResp.statusCode() < 200 || createResp.statusCode() >= 300) {
                String b = createResp.body() == null ? "" : createResp.body();
                if (b.toLowerCase(Locale.ROOT).contains("already exists")
                        || b.toLowerCase(Locale.ROOT).contains("exists")) {
                    log.info("Schema create raced; class already exists.");
                    return;
                }
                throw new WeaviateException("Schema creation failed HTTP " + createResp.statusCode() + " body=" + safeBody(b), null);
            }

        } catch (Exception e) {
            log.error("ensureSchema() failed", e);
            if (e instanceof WeaviateException we) throw we;
            throw new WeaviateException("ensureSchema() failed", e);
        }
    }

    private void ensureMbfcProperties(Set<String> existingProps) throws Exception {
        addPropertyIfMissing(existingProps, "mbfcBias", "text");
        addPropertyIfMissing(existingProps, "mbfcFactualReporting", "text");
        addPropertyIfMissing(existingProps, "mbfcCredibility", "text");
    }

    private void addPropertyIfMissing(Set<String> existingProps, String name, String dataType) throws Exception {
        if (existingProps.contains(name)) {
            return;
        }
        String body = String.format(
                Locale.ROOT,
                """
                { "name": "%s", "dataType": ["%s"] }
                """,
                name,
                dataType
        );

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/schema/" + CLASS_NAME + "/properties"))
                .timeout(httpTimeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            String b = resp.body() == null ? "" : resp.body();
            if (b.toLowerCase(Locale.ROOT).contains("already exists")
                    || b.toLowerCase(Locale.ROOT).contains("exists")) {
                log.info("Schema property create raced; property already exists.");
                return;
            }
            throw new WeaviateException("Schema property creation failed HTTP " + resp.statusCode()
                    + " body=" + safeBody(b), null);
        }
        log.info("Added Weaviate property {} to {}", name, CLASS_NAME);
    }

    public void indexArticleChunks(
            Article article,
            List<String> chunks,
            List<List<Double>> embeddings,
            String correlationId
    ) {
        if (article == null || article.getId() == null) {
            throw new WeaviateException("Article with non-null id is required", null);
        }
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        if (embeddings == null || chunks.size() != embeddings.size()) {
            throw new WeaviateException("Chunks size " + (chunks == null ? 0 : chunks.size())
                    + " != embeddings size " + (embeddings == null ? 0 : embeddings.size()), null);
        }

        try {
            log.info("Indexing {} chunks for article id={} into Weaviate", chunks.size(), article.getId());

            List<JsonNode> objects = new ArrayList<>(chunks.size());

            Instant published = article.getPublishedDate() != null ? article.getPublishedDate() : Instant.now();
            MbfcSnapshot mbfcSnapshot = extractMbfcSnapshot(article);

            for (int i = 0; i < chunks.size(); i++) {
                String chunkText = chunks.get(i);
                List<Double> vector = embeddings.get(i);

                String objectId = buildChunkObjectId(article.getId(), i);

                var obj = mapper.createObjectNode();
                obj.put("class", CLASS_NAME);
                obj.put("id", objectId);

                var props = obj.putObject("properties");
                props.put("text", chunkText);
                props.put("articleId", article.getId());
                props.put("articleUrl", nullToEmpty(article.getCanonicalUrl()));
                props.put("articleTitle", nullToEmpty(article.getTitle()));
                props.put("sourceName", article.getPublisher() != null ? nullToEmpty(article.getPublisher().getName()) : "");
                if (mbfcSnapshot.bias() != null) {
                    props.put("mbfcBias", mbfcSnapshot.bias());
                }
                if (mbfcSnapshot.factualReporting() != null) {
                    props.put("mbfcFactualReporting", mbfcSnapshot.factualReporting());
                }
                if (mbfcSnapshot.credibility() != null) {
                    props.put("mbfcCredibility", mbfcSnapshot.credibility());
                }
                props.put("publishedDate", published.toString()); // Use RFC3339 timestamp.
                props.put("chunkIndex", i);

                obj.set("vector", mapper.valueToTree(vector));
                objects.add(obj);
            }

            var batch = mapper.createObjectNode();
            batch.set("objects", mapper.valueToTree(objects));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/batch/objects"))
                    .timeout(httpTimeout)
                    .header("Content-Type", "application/json")
                    .header(CORRELATION_HEADER, safeCorrelation(correlationId))
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(batch), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.error("Weaviate batch error status={} body={}", resp.statusCode(), safeBody(resp.body()));
                throw new WeaviateException("Weaviate batch error " + resp.statusCode(), null);
            }

            // HTTP 200 can still include per-object errors.
            if (hasBatchErrors(resp.body())) {
                log.error("Weaviate batch returned per-object errors body={}", safeBody(resp.body()));
                throw new WeaviateException("Weaviate batch returned errors", null);
            }

        } catch (Exception e) {
            throw (e instanceof WeaviateException we) ? we : new WeaviateException("Failed to index into Weaviate", e);
        }
    }

    public List<ChunkResult> searchByEmbedding(
            List<Double> embedding,
            int limit,
            float minScore,
            String correlationId
    ) {
        if (embedding == null || embedding.isEmpty()) {
            return List.of();
        }

        try {
            String gql = buildSearchQuery(embedding, limit);

            var root = mapper.createObjectNode();
            root.put("query", gql);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/graphql"))
                    .timeout(httpTimeout)
                    .header("Content-Type", "application/json")
                    .header(CORRELATION_HEADER, safeCorrelation(correlationId))
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(root), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new WeaviateException("Weaviate search HTTP " + resp.statusCode() + " body=" + safeBody(resp.body()), null);
            }

            JsonNode respRoot = mapper.readTree(resp.body());
            JsonNode errors = respRoot.path("errors");
            if (errors.isArray() && !errors.isEmpty()) {
                log.warn("Weaviate GraphQL errors: {}", errors);
            }

            JsonNode data = respRoot.path("data").path("Get").path(CLASS_NAME);
            List<ChunkResult> results = new ArrayList<>();

            if (data.isArray()) {
                for (JsonNode n : data) {
                    double distance = n.path("_additional").path("distance").asDouble(1.0);
                    float score = 1f - (float) distance;
                    if (score < minScore) continue;

                    String text = n.path("text").asText("");
                    long articleId = n.path("articleId").asLong();
                    String url = n.path("articleUrl").asText("");
                    String title = n.path("articleTitle").asText("");
                    String sourceName = n.path("sourceName").asText("");
                    String publishedIso = n.path("publishedDate").asText(null);

                    LocalDateTime published = null;
                    if (publishedIso != null && !publishedIso.isBlank()) {
                        // Weaviate returns RFC3339; parse to UTC.
                        Instant inst = Instant.parse(publishedIso);
                        published = LocalDateTime.ofInstant(inst, ZoneOffset.UTC);
                    }

                    int chunkIndex = n.path("chunkIndex").asInt();

                    results.add(ChunkResult.builder()
                            .text(text)
                            .articleId(articleId)
                            .articleUrl(url)
                            .articleTitle(title)
                            .sourceName(sourceName)
                            .publishedDate(published)
                            .chunkIndex(chunkIndex)
                            .score(score)
                            .build());
                }
            }

            return results;
        } catch (Exception e) {
            throw new WeaviateException("Weaviate search failed", e);
        }
    }

    public List<String> getChunksForArticle(long articleId) {
        try {
            String gql = String.format(
                    Locale.US,
                    """
                    {
                      Get {
                        %s(
                          where: {
                            path: ["articleId"]
                            operator: Equal
                            valueInt: %d
                          },
                          limit: %d,
                          sort: [{ path: ["chunkIndex"], order: asc }]
                        ) {
                          text
                          chunkIndex
                        }
                      }
                    }
                    """,
                    CLASS_NAME,
                    articleId,
                    articleChunkLimit
            );

            var root = mapper.createObjectNode();
            root.put("query", gql);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/graphql"))
                    .timeout(httpTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(root), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new WeaviateException("Weaviate getChunksForArticle HTTP " + resp.statusCode()
                        + " body=" + safeBody(resp.body()), null);
            }

            JsonNode respRoot = mapper.readTree(resp.body());
            JsonNode errors = respRoot.path("errors");
            if (errors.isArray() && !errors.isEmpty()) {
                log.warn("Weaviate GraphQL errors in getChunksForArticle: {}", errors);
            }

            JsonNode data = respRoot.path("data").path("Get").path(CLASS_NAME);
            List<ChunkWithIndex> list = new ArrayList<>();

            if (data.isArray()) {
                for (JsonNode n : data) {
                    String text = n.path("text").asText("");
                    int idx = n.path("chunkIndex").asInt(0);
                    if (text == null || text.isBlank()) continue;
                    list.add(new ChunkWithIndex(idx, text));
                }
            }

            list.sort(Comparator.comparingInt(ChunkWithIndex::chunkIndex));
            List<String> chunks = new ArrayList<>(list.size());
            for (ChunkWithIndex c : list) chunks.add(c.text());
            return chunks;

        } catch (Exception e) {
            throw new WeaviateException("Weaviate getChunksForArticle failed", e);
        }
    }

    private record ChunkWithIndex(int chunkIndex, String text) {}

    private String buildSearchQuery(List<Double> embedding, int limit) throws Exception {
        String vectorJson = mapper.writeValueAsString(embedding);

        return String.format(
                Locale.US,
                "{ Get { %s(nearVector: {vector: %s}, limit: %d) { text articleId articleUrl articleTitle sourceName publishedDate chunkIndex _additional { distance } } } }",
                CLASS_NAME,
                vectorJson,
                limit
        );
    }

    private boolean hasBatchErrors(String body) {
        if (body == null || body.isBlank()) return false;
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode res = root.path("results");
            if (!res.isArray()) return false;

            for (JsonNode r : res) {
                JsonNode errors = r.path("errors");
                if (errors.isMissingNode() || errors.isNull()) continue;

                JsonNode errArr = errors.path("error");
                if (errArr.isArray() && errArr.size() > 0) {
                    return true;
                }
            }
            return false;
        } catch (Exception parseFail) {
            // Treat parse failures as errors to avoid silent drops.
            return true;
        }
    }

    private String safeCorrelation(String correlationId) {
        return (correlationId == null || correlationId.isBlank()) ? UUID.randomUUID().toString() : correlationId;
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private String safeBody(String body) {
        if (body == null) return "";
        return body.length() <= 2000 ? body : body.substring(0, 2000) + "...(truncated)";
    }

    private String buildChunkObjectId(Long articleId, int chunkIndex) {
        return UUID.nameUUIDFromBytes(("a:" + articleId + ":c:" + chunkIndex).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private MbfcSnapshot extractMbfcSnapshot(Article article) {
        if (article == null) {
            return new MbfcSnapshot(null, null, null);
        }
        try {
            if (article.getPublisher() == null || article.getPublisher().getMbfcSource() == null) {
                return new MbfcSnapshot(null, null, null);
            }
            var mbfc = article.getPublisher().getMbfcSource();
            return new MbfcSnapshot(
                    trimToNull(mbfc.getBias()),
                    trimToNull(mbfc.getFactualReporting()),
                    trimToNull(mbfc.getCredibility())
            );
        } catch (Exception e) {
            log.debug("MBFC source not available for article id={}, skipping MBFC fields", article.getId(), e);
            return new MbfcSnapshot(null, null, null);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record MbfcSnapshot(String bias, String factualReporting, String credibility) {}
}
