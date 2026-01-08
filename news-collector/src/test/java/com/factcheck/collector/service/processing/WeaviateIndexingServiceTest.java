package com.factcheck.collector.service.processing;

import com.factcheck.collector.domain.entity.Article;
import com.factcheck.collector.domain.entity.MbfcSource;
import com.factcheck.collector.domain.entity.Publisher;
import com.factcheck.collector.dto.ChunkResult;
import com.factcheck.collector.exception.WeaviateException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class WeaviateIndexingServiceTest {

    @Test
    void ensureSchema_whenClassExists_missingMbfcProps_addsMissingPropsOnly() throws Exception {
        AtomicInteger propertyPosts = new AtomicInteger(0);
        AtomicReference<String> lastPropertyBody = new AtomicReference<>("");

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/v1/schema", exchange -> {
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    respondJson(exchange, 200, """
                            {
                              "classes": [
                                {
                                  "class": "ArticleChunk",
                                  "properties": [
                                    { "name": "text" },
                                    { "name": "mbfcBias" }
                                  ]
                                }
                              ]
                            }
                            """);
                    return;
                }
                respondJson(exchange, 405, "{}");
            });

            server.createContext("/v1/schema/ArticleChunk/properties", exchange -> {
                if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    propertyPosts.incrementAndGet();
                    lastPropertyBody.set(readBody(exchange));
                    respondJson(exchange, 200, "{}");
                    return;
                }
                respondJson(exchange, 405, "{}");
            });

            server.start();

            WeaviateIndexingService service = serviceWithBaseUrl(server, 512);
            service.ensureSchema();

            assertThat(propertyPosts.get()).isEqualTo(2);
            assertThat(lastPropertyBody.get()).contains("dataType");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void ensureSchema_whenPropertyCreateRaces_alreadyExistsIsTreatedAsSuccess() throws Exception {
        AtomicInteger propertyPosts = new AtomicInteger(0);

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/v1/schema", exchange -> {
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    respondJson(exchange, 200, """
                            {
                              "classes": [
                                { "class": "ArticleChunk", "properties": [] }
                              ]
                            }
                            """);
                    return;
                }
                respondJson(exchange, 405, "{}");
            });

            server.createContext("/v1/schema/ArticleChunk/properties", exchange -> {
                if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    propertyPosts.incrementAndGet();
                    respondJson(exchange, 409, "{ \"error\": \"already exists\" }");
                    return;
                }
                respondJson(exchange, 405, "{}");
            });

            server.start();

            WeaviateIndexingService service = serviceWithBaseUrl(server, 512);
            assertThatCode(service::ensureSchema).doesNotThrowAnyException();
            assertThat(propertyPosts.get()).isEqualTo(3);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void ensureSchema_whenSchemaGetHttpError_throwsWeaviateException() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/v1/schema", exchange -> respondJson(exchange, 500, "nope"));
            server.start();

            WeaviateIndexingService service = serviceWithBaseUrl(server, 10);

            assertThatThrownBy(service::ensureSchema)
                    .isInstanceOf(WeaviateException.class)
                    .hasMessageContaining("Schema fetch failed HTTP 500");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void ensureSchema_whenMissing_createsClass() throws Exception {
        AtomicInteger postCalls = new AtomicInteger(0);
        AtomicReference<String> postBody = new AtomicReference<>("");

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/v1/schema", exchange -> {
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    respondJson(exchange, 200, "{ \"classes\": [] }");
                    return;
                }
                if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    postCalls.incrementAndGet();
                    postBody.set(readBody(exchange));
                    respondJson(exchange, 200, "{}");
                    return;
                }
                respondJson(exchange, 405, "{}");
            });
            server.start();

            WeaviateIndexingService service = serviceWithBaseUrl(server, 512);
            service.ensureSchema();

            assertThat(postCalls.get()).isEqualTo(1);
            assertThat(postBody.get()).contains("\"class\": \"ArticleChunk\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void ensureSchema_whenCreateRaces_alreadyExistsIsTreatedAsSuccess() throws Exception {
        AtomicInteger postCalls = new AtomicInteger(0);

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/v1/schema", exchange -> {
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    respondJson(exchange, 200, "{ \"classes\": [] }");
                    return;
                }
                if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    postCalls.incrementAndGet();
                    respondJson(exchange, 409, "{ \"error\": \"class already exists\" }");
                    return;
                }
                respondJson(exchange, 405, "{}");
            });
            server.start();

            WeaviateIndexingService service = serviceWithBaseUrl(server, 512);

            assertThatCode(service::ensureSchema).doesNotThrowAnyException();
            assertThat(postCalls.get()).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void indexArticleChunks_validations() {
        WeaviateIndexingService service = new WeaviateIndexingService(new ObjectMapper());
        assertThatThrownBy(() -> service.indexArticleChunks(null, List.of("c"), List.of(List.of(0.1)), "cid"))
                .isInstanceOf(WeaviateException.class)
                .hasMessageContaining("Article with non-null id is required");

        Article noId = new Article();
        assertThatThrownBy(() -> service.indexArticleChunks(noId, List.of("c"), List.of(List.of(0.1)), "cid"))
                .isInstanceOf(WeaviateException.class)
                .hasMessageContaining("Article with non-null id is required");

        Article a = new Article();
        a.setId(1L);

        assertThatCode(() -> service.indexArticleChunks(a, List.of(), List.of(), "cid"))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> service.indexArticleChunks(a, List.of("c1", "c2"), List.of(List.of(0.1)), "cid"))
                .isInstanceOf(WeaviateException.class)
                .hasMessageContaining("!= embeddings size");
    }

    @Test
    void indexArticleChunks_success_sendsCorrelationHeader_autogeneratesWhenBlank_andWritesMbfcFields() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>("");
        AtomicReference<String> capturedCorrelation = new AtomicReference<>("");

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/v1/batch/objects", exchange -> {
                capturedBody.set(readBody(exchange));
                capturedCorrelation.set(exchange.getRequestHeaders().getFirst(WeaviateIndexingService.CORRELATION_HEADER));
                respondJson(exchange, 200, "{ \"results\": [] }");
            });
            server.start();

            WeaviateIndexingService service = serviceWithBaseUrl(server, 512);

            Article article = articleWithMbfc(10L, "https://example.com/a", "T", Instant.parse("2024-01-01T00:00:00Z"), "Pub",
                    " Left ", " High ", " Medium ");

            service.indexArticleChunks(
                    article,
                    List.of("c0"),
                    List.of(List.of(0.1, 0.2)),
                    "   "
            );

            assertThat(capturedCorrelation.get()).isNotBlank();

            String expectedId0 = UUID.nameUUIDFromBytes("a:10:c:0".getBytes(StandardCharsets.UTF_8)).toString();
            assertThat(capturedBody.get()).contains("\"id\":\"" + expectedId0 + "\"");

            assertThat(capturedBody.get()).contains("\"mbfcBias\":\"Left\"");
            assertThat(capturedBody.get()).contains("\"mbfcFactualReporting\":\"High\"");
            assertThat(capturedBody.get()).contains("\"mbfcCredibility\":\"Medium\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void indexArticleChunks_whenHttpError_throws() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/v1/batch/objects", exchange -> respondJson(exchange, 500, "boom"));
            server.start();

            WeaviateIndexingService service = serviceWithBaseUrl(server, 512);
            Article article = article(1L);

            assertThatThrownBy(() ->
                    service.indexArticleChunks(
                            article,
                            List.of("c"),
                            List.of(List.of(0.1, 0.2)),
                            "cid"
                    )
            ).isInstanceOf(WeaviateException.class)
                    .hasMessageContaining("Weaviate batch error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void indexArticleChunks_whenBodyNotJson_hasBatchErrorsTreatsAsError() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/v1/batch/objects", exchange -> respondJson(exchange, 200, "not-json"));
            server.start();

            WeaviateIndexingService service = serviceWithBaseUrl(server, 512);
            Article article = article(1L);

            assertThatThrownBy(() ->
                    service.indexArticleChunks(
                            article,
                            List.of("c"),
                            List.of(List.of(0.1, 0.2)),
                            "cid"
                    )
            ).isInstanceOf(WeaviateException.class)
                    .hasMessageContaining("batch returned errors");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void indexArticleChunks_whenPerObjectBatchErrors_throws() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/v1/batch/objects", exchange -> respondJson(exchange, 200, """
                    {
                      "results": [
                        {
                          "result": { "status": "FAILED" },
                          "errors": { "error": [ { "message": "invalid object" } ] }
                        }
                      ]
                    }
                    """));
            server.start();

            WeaviateIndexingService service = serviceWithBaseUrl(server, 512);
            Article article = article(1L);

            assertThatThrownBy(() ->
                    service.indexArticleChunks(
                            article,
                            List.of("c"),
                            List.of(List.of(0.1, 0.2)),
                            "cid"
                    )
            ).isInstanceOf(WeaviateException.class)
                    .hasMessageContaining("batch returned errors");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void searchByEmbedding_emptyEmbedding_returnsEmpty_withoutCallingHttp() throws Exception {
        WeaviateIndexingService service = new WeaviateIndexingService(new ObjectMapper());
        setField(service, "baseUrl", "http://localhost:1");
        setField(service, "articleChunkLimit", 10);
        setField(service, "httpTimeout", Duration.ofSeconds(2));

        assertThat(service.searchByEmbedding(List.of(), 5, 0.0f, "cid")).isEmpty();
        assertThat(service.searchByEmbedding(null, 5, 0.0f, "cid")).isEmpty();
    }

    @Test
    void searchByEmbedding_httpError_throws() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/v1/graphql", exchange -> respondJson(exchange, 500, "error"));
            server.start();

            WeaviateIndexingService service = serviceWithBaseUrl(server, 10);

            assertThatThrownBy(() -> service.searchByEmbedding(List.of(0.1, 0.2), 5, 0.0f, "cid"))
                    .isInstanceOf(WeaviateException.class)
                    .hasMessageContaining("Weaviate search failed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void searchByEmbedding_filtersByScore_parsesPublishedDate_andHandlesBlankPublishedDate() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>("");

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/v1/graphql", exchange -> {
                capturedBody.set(readBody(exchange));
                respondJson(exchange, 200, """
                        {
                          "data": {
                            "Get": {
                              "ArticleChunk": [
                                {
                                  "text": "keep",
                                  "articleId": 1,
                                  "articleUrl": "url1",
                                  "articleTitle": "title1",
                                  "sourceName": "source1",
                                  "publishedDate": "2024-01-01T00:00:00Z",
                                  "chunkIndex": 1,
                                  "_additional": { "distance": 0.2 }
                                },
                                {
                                  "text": "keep2",
                                  "articleId": 2,
                                  "articleUrl": "url2",
                                  "articleTitle": "title2",
                                  "sourceName": "source2",
                                  "publishedDate": "",
                                  "chunkIndex": 2,
                                  "_additional": { "distance": 0.2 }
                                },
                                {
                                  "text": "discard",
                                  "articleId": 3,
                                  "articleUrl": "url3",
                                  "articleTitle": "title3",
                                  "sourceName": "source3",
                                  "publishedDate": "2024-01-01T00:00:00Z",
                                  "chunkIndex": 3,
                                  "_additional": { "distance": 0.9 }
                                }
                              ]
                            }
                          }
                        }
                        """);
            });
            server.start();

            WeaviateIndexingService service = serviceWithBaseUrl(server, 10);

            List<ChunkResult> results = service.searchByEmbedding(
                    List.of(0.1, 0.2),
                    5,
                    0.3f,
                    "cid-1"
            );

            assertThat(results).hasSize(2);
            assertThat(results.get(0).getText()).isEqualTo("keep");
            assertThat(results.get(0).getPublishedDate())
                    .isEqualTo(LocalDateTime.ofInstant(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC));
            assertThat(results.get(1).getPublishedDate()).isNull();

            assertThat(capturedBody.get()).contains("nearVector");
            assertThat(capturedBody.get()).contains("limit");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void getChunksForArticle_ordersAndFiltersBlankText_andRespectsLimit() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>("");

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/v1/graphql", exchange -> {
                capturedBody.set(readBody(exchange));
                respondJson(exchange, 200, """
                        {
                          "data": {
                            "Get": {
                              "ArticleChunk": [
                                { "text": "second", "chunkIndex": 1 },
                                { "text": "first",  "chunkIndex": 0 },
                                { "text": "",       "chunkIndex": 2 }
                              ]
                            }
                          }
                        }
                        """);
            });
            server.start();

            WeaviateIndexingService service = serviceWithBaseUrl(server, 5);

            List<String> chunks = service.getChunksForArticle(9L);

            assertThat(chunks).containsExactly("first", "second");
            assertThat(capturedBody.get()).contains("limit: 5");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void getChunksForArticle_httpError_throws() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/v1/graphql", exchange -> respondJson(exchange, 500, "error"));
            server.start();

            WeaviateIndexingService service = serviceWithBaseUrl(server, 5);

            assertThatThrownBy(() -> service.getChunksForArticle(1L))
                    .isInstanceOf(WeaviateException.class)
                    .hasMessageContaining("getChunksForArticle failed");
        } finally {
            server.stop(0);
        }
    }

    // Helpers

    private WeaviateIndexingService serviceWithBaseUrl(HttpServer server, int chunkLimit) throws Exception {
        WeaviateIndexingService service = new WeaviateIndexingService(new ObjectMapper());
        setField(service, "baseUrl", "http://localhost:" + server.getAddress().getPort());
        setField(service, "articleChunkLimit", chunkLimit);
        setField(service, "httpTimeout", Duration.ofSeconds(3));
        return service;
    }

    private static Article article(Long id) {
        Article a = new Article();
        a.setId(id);
        a.setCanonicalUrl("u");
        a.setTitle("t");
        a.setPublishedDate(Instant.parse("2024-01-01T00:00:00Z"));
        Publisher p = new Publisher();
        p.setName("Pub");
        a.setPublisher(p);
        return a;
    }

    private static Article articleWithMbfc(
            Long id,
            String url,
            String title,
            Instant publishedDate,
            String publisherName,
            String bias,
            String factual,
            String credibility
    ) {
        Article a = new Article();
        a.setId(id);
        a.setCanonicalUrl(url);
        a.setTitle(title);
        a.setPublishedDate(publishedDate);

        MbfcSource mbfc = new MbfcSource();
        mbfc.setBias(bias);
        mbfc.setFactualReporting(factual);
        mbfc.setCredibility(credibility);

        Publisher p = new Publisher();
        p.setName(publisherName);
        p.setMbfcSource(mbfc);

        a.setPublisher(p);
        return a;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void respondJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] responseBytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, responseBytes.length);
        exchange.getResponseBody().write(responseBytes);
        exchange.close();
    }
}