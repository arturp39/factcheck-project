package com.factcheck.collector.service.processing;

import com.factcheck.collector.domain.entity.Article;
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
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class WeaviateIndexingServiceTest {

    @Test
    void ensureSchema_whenClassExists_doesNotCreate() throws Exception {
        AtomicInteger postCalls = new AtomicInteger(0);

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/schema", exchange -> {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                respondJson(exchange, 200, """
                        {
                          "classes": [
                            { "class": "ArticleChunk" }
                          ]
                        }
                        """);
                return;
            }
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                postCalls.incrementAndGet();
                respondJson(exchange, 200, "{}");
                return;
            }
            respondJson(exchange, 405, "{}");
        });
        server.start();

        WeaviateIndexingService service = serviceWithBaseUrl(server, 512);
        service.ensureSchema();

        assertThat(postCalls.get()).isZero();

        server.stop(0);
    }

    @Test
    void ensureSchema_whenMissing_createsClass() throws Exception {
        AtomicInteger postCalls = new AtomicInteger(0);
        AtomicReference<String> postBody = new AtomicReference<>("");

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/schema", exchange -> {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                respondJson(exchange, 200, """
                        { "classes": [] }
                        """);
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

        server.stop(0);
    }

    @Test
    void ensureSchema_whenCreateRaces_alreadyExistsIsTreatedAsSuccess() throws Exception {
        AtomicInteger postCalls = new AtomicInteger(0);

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/schema", exchange -> {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                respondJson(exchange, 200, """
                        { "classes": [] }
                        """);
                return;
            }
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                postCalls.incrementAndGet();
                // Simulate a create race.
                respondJson(exchange, 409, """
                        { "error": "class already exists" }
                        """);
                return;
            }
            respondJson(exchange, 405, "{}");
        });
        server.start();

        WeaviateIndexingService service = serviceWithBaseUrl(server, 512);

        assertThatCode(service::ensureSchema).doesNotThrowAnyException();
        assertThat(postCalls.get()).isEqualTo(1);

        server.stop(0);
    }

    @Test
    void indexArticleChunks_success_sendsCorrelationHeaderAndDeterministicIds_andNoBatchErrors() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>("");
        AtomicReference<String> capturedCorrelation = new AtomicReference<>("");

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/batch/objects", exchange -> {
            capturedBody.set(readBody(exchange));
            capturedCorrelation.set(exchange.getRequestHeaders().getFirst(WeaviateIndexingService.CORRELATION_HEADER));
            // Return an empty result set.
            respondJson(exchange, 200, """
                    { "results": [] }
                    """);
        });
        server.start();

        WeaviateIndexingService service = serviceWithBaseUrl(server, 512);

        Article article = article(10L, "https://example.com/a", "T", Instant.parse("2024-01-01T00:00:00Z"), "Pub");
        List<String> chunks = List.of("c0", "c1");
        List<List<Double>> embeddings = List.of(List.of(0.1, 0.2), List.of(0.3, 0.4));

        service.indexArticleChunks(article, chunks, embeddings, "cid-123");

        assertThat(capturedCorrelation.get()).isEqualTo("cid-123");

        String expectedId0 = UUID.nameUUIDFromBytes("a:10:c:0".getBytes(StandardCharsets.UTF_8)).toString();
        String expectedId1 = UUID.nameUUIDFromBytes("a:10:c:1".getBytes(StandardCharsets.UTF_8)).toString();

        assertThat(capturedBody.get()).contains("\"id\":\"" + expectedId0 + "\"");
        assertThat(capturedBody.get()).contains("\"id\":\"" + expectedId1 + "\"");
        assertThat(capturedBody.get()).contains("\"class\":\"ArticleChunk\"");

        server.stop(0);
    }

    @Test
    void indexArticleChunks_whenHttpError_throws() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/batch/objects", exchange -> respondJson(exchange, 500, "boom"));
        server.start();

        WeaviateIndexingService service = serviceWithBaseUrl(server, 512);

        Article article = article(1L, "u", "t", Instant.parse("2024-01-01T00:00:00Z"), "Pub");

        assertThatThrownBy(() ->
                service.indexArticleChunks(
                        article,
                        List.of("c"),
                        List.of(List.of(0.1, 0.2)),
                        "cid"
                )
        ).isInstanceOf(WeaviateException.class);

        server.stop(0);
    }

    @Test
    void indexArticleChunks_whenPerObjectBatchErrors_throws() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/batch/objects", exchange -> respondJson(exchange, 200, """
                {
                  "results": [
                    {
                      "result": { "status": "FAILED" },
                      "errors": {
                        "error": [
                          { "message": "invalid object" }
                        ]
                      }
                    }
                  ]
                }
                """));
        server.start();

        WeaviateIndexingService service = serviceWithBaseUrl(server, 512);

        Article article = article(1L, "u", "t", Instant.parse("2024-01-01T00:00:00Z"), "Pub");

        assertThatThrownBy(() ->
                service.indexArticleChunks(
                        article,
                        List.of("c"),
                        List.of(List.of(0.1, 0.2)),
                        "cid"
                )
        ).isInstanceOf(WeaviateException.class)
                .hasMessageContaining("batch returned errors");

        server.stop(0);
    }

    @Test
    void searchByEmbedding_emptyEmbedding_returnsEmpty_withoutCallingHttp() throws Exception {
        // No server needed; method returns early.
        WeaviateIndexingService service = new WeaviateIndexingService(new ObjectMapper());
        setField(service, "baseUrl", "http://localhost:1");
        setField(service, "articleChunkLimit", 10);
        setField(service, "httpTimeout", Duration.ofSeconds(2));

        assertThat(service.searchByEmbedding(List.of(), 5, 0.0f, "cid")).isEmpty();
        assertThat(service.searchByEmbedding(null, 5, 0.0f, "cid")).isEmpty();
    }

    @Test
    void searchByEmbedding_filtersByScore_parsesPublishedDate_andSendsNearVectorQuery() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>("");

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
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
                              "text": "discard",
                              "articleId": 2,
                              "articleUrl": "url2",
                              "articleTitle": "title2",
                              "sourceName": "source2",
                              "publishedDate": "2024-01-01T00:00:00Z",
                              "chunkIndex": 2,
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
                0.3f, // Score is 1 minus distance.
                "cid-1"
        );

        assertThat(results).hasSize(1);
        ChunkResult r = results.getFirst();
        assertThat(r.getText()).isEqualTo("keep");
        assertThat(r.getScore()).isGreaterThanOrEqualTo(0.3f);
        assertThat(r.getPublishedDate()).isEqualTo(LocalDateTime.ofInstant(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC));
        assertThat(capturedBody.get()).contains("nearVector");
        assertThat(capturedBody.get()).contains("limit");

        server.stop(0);
    }

    @Test
    void getChunksForArticle_ordersAndFiltersBlankText_andRespectsLimit() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>("");

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
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

        server.stop(0);
    }

    @Test
    void getChunksForArticle_httpError_throws() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/graphql", exchange -> respondJson(exchange, 500, "error"));
        server.start();

        WeaviateIndexingService service = serviceWithBaseUrl(server, 5);

        assertThatThrownBy(() -> service.getChunksForArticle(1L))
                .isInstanceOf(WeaviateException.class);

        server.stop(0);
    }

    // Helpers.

    private WeaviateIndexingService serviceWithBaseUrl(HttpServer server, int chunkLimit) throws Exception {
        WeaviateIndexingService service = new WeaviateIndexingService(new ObjectMapper());
        setField(service, "baseUrl", "http://localhost:" + server.getAddress().getPort());
        setField(service, "articleChunkLimit", chunkLimit);
        setField(service, "httpTimeout", Duration.ofSeconds(3));
        return service;
    }

    private static Article article(Long id, String url, String title, Instant publishedDate, String publisherName) {
        Article a = new Article();
        a.setId(id);
        a.setCanonicalUrl(url);
        a.setTitle(title);
        a.setPublishedDate(publishedDate);

        Publisher p = new Publisher();
        p.setName(publisherName);
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