package com.factcheck.collector.integration.catalog.newsapi;

import com.factcheck.collector.integration.catalog.newsapi.dto.NewsApiSourcesResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NewsApiClientTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void fetchSources_buildsExpectedRequest() throws Exception {
        AtomicReference<URI> requestUri = new AtomicReference<>();
        server.createContext("/v2/top-headlines/sources", exchange -> {
            requestUri.set(exchange.getRequestURI());
            byte[] body = """
                    {"status":"ok","sources":[{"id":"abc","name":"ABC News"}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        NewsApiProperties properties = new NewsApiProperties();
        properties.setApiKey("key-123");
        properties.setBaseUrl("http://localhost:" + port + "/v2/top-headlines");

        NewsApiClient client = new NewsApiClient(properties, new ObjectMapper());

        NewsApiSourcesResponse response = client.fetchSources("en");

        assertThat(response.status()).isEqualTo("ok");
        assertThat(response.sources()).hasSize(1);
        assertThat(requestUri.get().getPath()).isEqualTo("/v2/top-headlines/sources");

        Map<String, String> query = parseQuery(requestUri.get().getQuery());
        assertThat(query.get("apiKey")).isEqualTo("key-123");
        assertThat(query.get("language")).isEqualTo("en");
    }

    @Test
    void fetchEverything_buildsExpectedRequest() throws Exception {
        AtomicReference<URI> requestUri = new AtomicReference<>();
        server.createContext("/v2/everything", exchange -> {
            requestUri.set(exchange.getRequestURI());
            byte[] body = """
                    {"status":"ok","totalResults":1,"articles":[]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        NewsApiProperties properties = new NewsApiProperties();
        properties.setApiKey("key-456");
        properties.setBaseUrl("http://localhost:" + port + "/v2/everything");

        NewsApiClient client = new NewsApiClient(properties, new ObjectMapper());

        client.fetchEverything("abc,bbc", "publishedAt", 2);

        assertThat(requestUri.get().getPath()).isEqualTo("/v2/everything");

        Map<String, String> query = parseQuery(requestUri.get().getQuery());
        assertThat(query.get("apiKey")).isEqualTo("key-456");
        assertThat(query.get("sources")).isEqualTo("abc,bbc");
        assertThat(query.get("sortBy")).isEqualTo("publishedAt");
        assertThat(query.get("page")).isEqualTo("2");
        assertThat(query).doesNotContainKey("pageSize");
    }

    @Test
    void fetchSources_throwsWhenApiKeyMissing() {
        NewsApiProperties properties = new NewsApiProperties();
        properties.setApiKey(" ");
        properties.setBaseUrl("http://localhost:" + port + "/v2");

        NewsApiClient client = new NewsApiClient(properties, new ObjectMapper());

        assertThatThrownBy(() -> client.fetchSources("en"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NEWSAPI_API_KEY");
    }

    @Test
    void fetchEverything_throwsOnErrorStatus() throws Exception {
        server.createContext("/v2/everything", exchange -> {
            byte[] body = """
                    {"status":"error","code":"apiKeyInvalid","message":"Invalid key"}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        NewsApiProperties properties = new NewsApiProperties();
        properties.setApiKey("key-789");
        properties.setBaseUrl("http://localhost:" + port + "/v2");

        NewsApiClient client = new NewsApiClient(properties, new ObjectMapper());

        assertThatThrownBy(() -> client.fetchEverything("abc", "publishedAt", 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NewsAPI error");
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isBlank()) {
            return params;
        }
        for (String part : query.split("&")) {
            String[] kv = part.split("=", 2);
            String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String value = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            params.put(key, value);
        }
        return params;
    }
}