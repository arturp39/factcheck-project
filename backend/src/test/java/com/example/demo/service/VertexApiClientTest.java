package com.example.demo.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class VertexApiClientTest {

    private HttpServer server;
    private AtomicReference<String> authHeader;

    @BeforeEach
    void setUp() throws IOException {
        authHeader = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/vertex", exchange -> {
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void postJson_sendsBearerTokenAndReturnsResponse() throws Exception {
        VertexAuthHelper authHelper = Mockito.mock(VertexAuthHelper.class);
        when(authHelper.fetchAccessToken()).thenReturn("token-xyz");

        VertexApiClient client = new VertexApiClient(authHelper);
        String endpoint = "http://localhost:" + server.getAddress().getPort() + "/vertex";

        var response = client.postJson(endpoint, "{\"foo\":\"bar\"}");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(new String(response.body().getBytes(StandardCharsets.UTF_8))).contains("foo");
        assertThat(authHeader.get()).isEqualTo("Bearer token-xyz");
    }
}