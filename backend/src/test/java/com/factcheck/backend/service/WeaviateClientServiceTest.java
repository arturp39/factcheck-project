package com.factcheck.backend.service;

import com.factcheck.backend.config.WeaviateProperties;
import com.factcheck.backend.exception.WeaviateException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class WeaviateClientServiceTest {

    @Test
    void parseEvidenceChunks_filtersByDistanceAndMapsFields() throws Exception {
        WeaviateProperties props = new WeaviateProperties();
        props.setBaseUrl("http://localhost:8080");
        props.setApiKey(null);
        props.setMaxDistance(0.5f);

        WeaviateClientService service = new WeaviateClientService(props);

        String json = """
                {
                  "data": {
                    "Get": {
                      "ArticleChunk": [
                        {
                          "text": "Chunk1",
                          "articleTitle": "Title1",
                          "sourceName": "Source1",
                          "publishedDate": "2024-01-02T03:04:05Z",
                          "mbfcBias": "left",
                          "mbfcFactualReporting": "mixed",
                          "mbfcCredibility": "medium",
                          "_additional": { "distance": 0.3 }
                        },
                        {
                          "text": "TooFar",
                          "articleTitle": "Title2",
                          "sourceName": "Source2",
                          "_additional": { "distance": 0.9 }
                        }
                      ]
                    }
                  }
                }
                """;

        List<WeaviateClientService.EvidenceChunk> chunks =
                service.parseEvidenceChunks(json);

        assertThat(chunks).hasSize(1);
        WeaviateClientService.EvidenceChunk c = chunks.get(0);
        assertThat(c.title()).isEqualTo("Title1");
        assertThat(c.content()).isEqualTo("Chunk1");
        assertThat(c.source()).isEqualTo("Source1");
        assertThat(c.publishedAt()).isEqualTo(LocalDateTime.of(2024, 1, 2, 3, 4, 5));
        assertThat(c.mbfcBias()).isEqualTo("left");
        assertThat(c.mbfcFactualReporting()).isEqualTo("mixed");
        assertThat(c.mbfcCredibility()).isEqualTo("medium");
    }

    @Test
    void parseEvidenceChunks_throwsOnErrorsField() {
        WeaviateProperties props = new WeaviateProperties();
        props.setBaseUrl("http://localhost:8080");
        props.setApiKey(null);
        props.setMaxDistance(0.5f);

        WeaviateClientService service = new WeaviateClientService(props);

        String json = """
                {
                  "errors": [
                    { "message": "Something went wrong" }
                  ]
                }
                """;

        assertThatThrownBy(() -> service.parseEvidenceChunks(json))
                .isInstanceOf(WeaviateException.class)
                .hasMessageContaining("Weaviate GraphQL returned errors");
    }

    @Test
    void parseEvidenceChunks_returnsEmptyWhenNoArray() throws Exception {
        WeaviateProperties props = new WeaviateProperties();
        props.setBaseUrl("http://localhost:8080");
        props.setApiKey(null);
        props.setMaxDistance(0.5f);

        WeaviateClientService service = new WeaviateClientService(props);

        String json = """
                {
                  "data": {
                    "Get": { "ArticleChunk": { "unexpected": true } }
                  }
                }
                """;

        List<WeaviateClientService.EvidenceChunk> chunks = service.parseEvidenceChunks(json);

        assertThat(chunks).isEmpty();
    }

    @Test
    void insertArticleChunk_postsObjectToWeaviate() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        TestServer server = new TestServer("/v1/objects", 200, "{\"result\":\"ok\"}", requestBody);

        WeaviateProperties props = new WeaviateProperties();
        props.setBaseUrl(server.baseUrl());
        props.setApiKey(null);

        WeaviateClientService service = new WeaviateClientService(props);

        String response = service.insertArticleChunk(
                "Title",
                "Content",
                "source",
                new float[]{0.1f, 0.2f}
        );

        assertThat(response).contains("ok");
        assertThat(requestBody.get()).contains("\"articleTitle\":\"Title\"");
        assertThat(requestBody.get()).contains("\"vector\"");
        assertThat(server.capturedCorrelation()).isNotBlank();

        server.stop();
    }

    @Test
    void insertArticleChunk_throwsOnNon2xx() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        TestServer server = new TestServer("/v1/objects", 500, "fail", requestBody);

        WeaviateProperties props = new WeaviateProperties();
        props.setBaseUrl(server.baseUrl());
        WeaviateClientService service = new WeaviateClientService(props);

        assertThatThrownBy(() -> service.insertArticleChunk(
                "Title",
                "Content",
                "source",
                new float[]{1f}
        )).isInstanceOf(WeaviateException.class);

        assertThat(requestBody.get()).contains("\"Content\"");
        server.stop();
    }

    @Test
    void insertArticleChunk_defaultsManualSourceWhenBlank() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        TestServer server = new TestServer("/v1/objects", 200, "{\"ok\":true}", requestBody);

        WeaviateProperties props = new WeaviateProperties();
        props.setBaseUrl(server.baseUrl());

        WeaviateClientService service = new WeaviateClientService(props);
        String response = service.insertArticleChunk("T", "C", "", new float[]{0.5f});

        assertThat(response).contains("ok");
        assertThat(requestBody.get()).contains("\"sourceName\":\"manual\"");
        assertThat(server.capturedCorrelation()).isNotBlank();
        server.stop();
    }

    @Test
    void insertArticleChunk_rejectsEmptyVector() {
        WeaviateProperties props = new WeaviateProperties();
        props.setBaseUrl("http://localhost:1234");
        WeaviateClientService service = new WeaviateClientService(props);

        assertThatThrownBy(() -> service.insertArticleChunk("t", "c", "s", new float[]{}))
                .isInstanceOf(WeaviateException.class)
                .hasMessageContaining("Vector must not be empty");
    }

    @Test
    void searchByVector_postsGraphqlAndReturnsBody() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        TestServer server = new TestServer("/v1/graphql", 200, "{\"data\":{\"Get\":{}}}", requestBody);

        WeaviateProperties props = new WeaviateProperties();
        props.setBaseUrl(server.baseUrl());
        props.setApiKey("secret");
        props.setMaxDistance(0.4f);

        WeaviateClientService service = new WeaviateClientService(props);
        String response = service.searchByVector(new float[]{0.1f, 0.2f}, 2);

        assertThat(response).contains("\"data\"");
        assertThat(requestBody.get()).contains("nearVector");
        assertThat(requestBody.get()).contains("0.1");
        assertThat(requestBody.get()).contains("0.2");
        assertThat(server.capturedApiKey()).isEqualTo("secret");
        assertThat(server.capturedCorrelation()).isNotBlank();
        server.stop();
    }

    @Test
    void searchByVector_throwsOnNon2xx() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        TestServer server = new TestServer("/v1/graphql", 500, "fail", requestBody);

        WeaviateProperties props = new WeaviateProperties();
        props.setBaseUrl(server.baseUrl());
        props.setMaxDistance(0.2f);

        WeaviateClientService service = new WeaviateClientService(props);

        assertThatThrownBy(() -> service.searchByVector(new float[]{1.1f}, 1))
                .isInstanceOf(WeaviateException.class)
                .hasMessageContaining("Weaviate GraphQL HTTP 500");

        assertThat(requestBody.get()).contains("1.1");
        server.stop();
    }

    private static class TestServer {
        private final com.sun.net.httpserver.HttpServer server;
        private final AtomicReference<String> capturedBody;
        private final AtomicReference<String> capturedApiKey = new AtomicReference<>();
        private final AtomicReference<String> capturedCorrelation = new AtomicReference<>();

        TestServer(String path, int status, String body, AtomicReference<String> capturedBody) throws IOException {
            this.capturedBody = capturedBody;
            this.server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(0), 0);
            this.server.createContext(path, exchange -> {
                byte[] bytes = exchange.getRequestBody().readAllBytes();
                capturedBody.set(new String(bytes, StandardCharsets.UTF_8));
                capturedApiKey.set(exchange.getRequestHeaders().getFirst("X-API-KEY"));
                capturedCorrelation.set(exchange.getRequestHeaders().getFirst(WeaviateClientService.CORRELATION_HEADER));
                exchange.sendResponseHeaders(status, body.getBytes(StandardCharsets.UTF_8).length);
                exchange.getResponseBody().write(body.getBytes(StandardCharsets.UTF_8));
                exchange.close();
            });
            this.server.start();
        }

        String baseUrl() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        String capturedApiKey() {
            return capturedApiKey.get();
        }

        String capturedCorrelation() {
            return capturedCorrelation.get();
        }

        void stop() {
            server.stop(0);
        }
    }
}
