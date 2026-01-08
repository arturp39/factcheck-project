package com.factcheck.collector.integration.ingestion;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import crawlercommons.robots.BaseRobotRules;
import crawlercommons.robots.SimpleRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RobotsServiceTest {

    @Test
    void disallowsPathsDefinedInRobots_fromCache() {
        RobotsService service = new RobotsService("TestBot");

        SimpleRobotRulesParser parser = new SimpleRobotRulesParser();
        BaseRobotRules rules = parser.parseContent(
                "https://example.com/robots.txt",
                """
                        User-agent: *
                        Disallow: /blocked
                        """.getBytes(StandardCharsets.UTF_8),
                "text/plain",
                "TestBot"
        );

        Object cachedRules = newCachedRules(rules, Instant.now());
        ConcurrentHashMap<String, Object> cache = new ConcurrentHashMap<>();
        cache.put("https://example.com:443", cachedRules);
        ReflectionTestUtils.setField(service, "rulesCache", cache);

        assertThat(service.isAllowed("https://example.com/allowed/page")).isTrue();
        assertThat(service.isAllowed("https://example.com/blocked")).isFalse();
    }

    @Test
    void isAllowed_whenSchemeMissingOrUnsupported_allows() {
        RobotsService service = new RobotsService("TestBot");
        assertThat(service.isAllowed("example.com/path")).isTrue();
        assertThat(service.isAllowed("ftp://example.com/path")).isTrue();
    }

    @Test
    void isAllowed_fetchesRobots_200_andCachesRules() throws Exception {
        AtomicInteger robotsCalls = new AtomicInteger(0);

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/robots.txt", exchange -> {
                robotsCalls.incrementAndGet();
                respond(exchange, 200, "text/plain", """
                        User-agent: *
                        Disallow: /blocked
                        """);
            });
            server.start();

            int port = server.getAddress().getPort();
            RobotsService service = new RobotsService("TestBot");

            // First call: fetch + parse.
            assertThat(service.isAllowed("http://localhost:" + port + "/allowed")).isTrue();
            assertThat(service.isAllowed("http://localhost:" + port + "/blocked")).isFalse();

            // Second call: should use cache (no additional fetch).
            assertThat(service.isAllowed("http://localhost:" + port + "/blocked/again")).isFalse();

            assertThat(robotsCalls.get()).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void isAllowed_whenRobots404_allowsAll() throws Exception {
        AtomicInteger robotsCalls = new AtomicInteger(0);

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/robots.txt", exchange -> {
                robotsCalls.incrementAndGet();
                respond(exchange, 404, "text/plain", "not found");
            });
            server.start();

            int port = server.getAddress().getPort();
            RobotsService service = new RobotsService("TestBot");

            assertThat(service.isAllowed("http://localhost:" + port + "/anything")).isTrue();
            assertThat(robotsCalls.get()).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void isAllowed_whenRobots5xx_allowsAll() throws Exception {
        AtomicInteger robotsCalls = new AtomicInteger(0);

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/robots.txt", exchange -> {
                robotsCalls.incrementAndGet();
                respond(exchange, 500, "text/plain", "error");
            });
            server.start();

            int port = server.getAddress().getPort();
            RobotsService service = new RobotsService("TestBot");

            assertThat(service.isAllowed("http://localhost:" + port + "/anything")).isTrue();
            assertThat(robotsCalls.get()).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void isAllowed_whenCacheExpired_refetches() throws Exception {
        AtomicInteger robotsCalls = new AtomicInteger(0);

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/robots.txt", exchange -> {
                robotsCalls.incrementAndGet();
                respond(exchange, 200, "text/plain", """
                        User-agent: *
                        Disallow: /blocked
                        """);
            });
            server.start();

            int port = server.getAddress().getPort();
            RobotsService service = new RobotsService("TestBot");

            String key = "http://localhost:" + port;

            SimpleRobotRules allowAll = new SimpleRobotRules(SimpleRobotRules.RobotRulesMode.ALLOW_ALL);
            Object expired = newCachedRules(allowAll, Instant.now().minus(Duration.ofDays(2)));

            @SuppressWarnings("unchecked")
            Map<String, Object> cache = (Map<String, Object>) ReflectionTestUtils.getField(service, "rulesCache");
            cache.put(key, expired);

            assertThat(service.isAllowed("http://localhost:" + port + "/blocked")).isFalse();
            assertThat(service.isAllowed("http://localhost:" + port + "/blocked2")).isFalse();

            assertThat(robotsCalls.get()).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    private static Object newCachedRules(BaseRobotRules rules, Instant fetchedAt) {
        try {
            Class<?> cachedRulesClass =
                    Class.forName("com.factcheck.collector.integration.ingestion.RobotsService$CachedRules");
            Constructor<?> constructor = cachedRulesClass.getDeclaredConstructor(BaseRobotRules.class, Instant.class);
            constructor.setAccessible(true);
            return constructor.newInstance(rules, fetchedAt);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to construct cached rules for test", e);
        }
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] responseBytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, responseBytes.length);
        exchange.getResponseBody().write(responseBytes);
        exchange.close();
    }
}