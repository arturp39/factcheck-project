package com.factcheck.collector.integration.robots;

import crawlercommons.robots.BaseRobotRules;
import crawlercommons.robots.SimpleRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class RobotsService {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final Duration CACHE_TTL = Duration.ofHours(12);

    private static final BaseRobotRules ALLOW_ALL_RULES =
            new SimpleRobotRules(SimpleRobotRules.RobotRulesMode.ALLOW_ALL);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private final SimpleRobotRulesParser parser = new SimpleRobotRulesParser();

    private final Map<String, CachedRules> rulesCache = new ConcurrentHashMap<>();

    private final String userAgent;

    public RobotsService(
            @Value("${crawler.user-agent:FactCheckCollector/1.0 (+https://example.com)}")
            String userAgent
    ) {
        this.userAgent = userAgent;
    }

    public boolean isAllowed(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            String scheme = uri.getScheme();

            if (host == null || scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
                return true;
            }

            host = host.toLowerCase(Locale.ROOT);
            String schemeLower = scheme.toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            String key = buildKey(schemeLower, host, port);

            CachedRules cached = rulesCache.get(key);
            if (cached == null || cached.isExpired()) {
                BaseRobotRules rules = fetchRulesForHost(schemeLower, host, port);
                cached = new CachedRules(rules, Instant.now());
                rulesCache.put(key, cached);
            }

            return cached.rules.isAllowed(url);

        } catch (Exception e) {
            log.warn("Failed to evaluate robots.txt for url={}. Defaulting to ALLOW. Reason: {}",
                    url, e.toString());
            return true;
        }
    }

    private String buildKey(String scheme, String host, int port) {
        int effectivePort = port;
        if (effectivePort == -1) {
            effectivePort = scheme.equals("https") ? 443 : 80;
        }
        return scheme + "://" + host + ":" + effectivePort;
    }

    private BaseRobotRules fetchRulesForHost(String scheme, String host, int port) {
        String baseUrl = scheme + "://" + host + (port == -1 ? "" : ":" + port);
        String robotsUrl = baseUrl + "/robots.txt";
        log.info("Fetching robots.txt from {}", robotsUrl);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(robotsUrl))
                .GET()
                .header("User-Agent", userAgent)
                .timeout(TIMEOUT)
                .build();

        try {
            HttpResponse<byte[]> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                byte[] body = response.body();
                String contentType = response.headers()
                        .firstValue("Content-Type")
                        .orElse("text/plain");
                return parser.parseContent(
                        robotsUrl,
                        body,
                        contentType,
                        userAgent
                );
            } else if (status == 404) {
                log.info("No robots.txt (404) for {}. Treating as all allowed.", baseUrl);
                return ALLOW_ALL_RULES;
            } else {
                log.warn("Non-OK status {} while fetching robots.txt from {}. Defaulting to ALLOW.",
                        status, robotsUrl);
                return ALLOW_ALL_RULES;
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted fetching robots.txt from {}. Defaulting to ALLOW. Reason: {}",
                    robotsUrl, e.toString());
            return ALLOW_ALL_RULES;
        } catch (IOException e) {
            log.warn("Error fetching robots.txt from {}. Defaulting to ALLOW. Reason: {}",
                    robotsUrl, e.toString());
            return ALLOW_ALL_RULES;
        }
    }

    private static final class CachedRules {
        private final BaseRobotRules rules;
        private final Instant fetchedAt;

        private CachedRules(BaseRobotRules rules, Instant fetchedAt) {
            this.rules = rules;
            this.fetchedAt = fetchedAt;
        }

        private boolean isExpired() {
            return fetchedAt.plus(CACHE_TTL).isBefore(Instant.now());
        }
    }
}