package com.factcheck.collector.integration.ingestion;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class RobotsServiceTest {

    @Test
    void disallowsPathsDefinedInRobots() {
        RobotsService service = new RobotsService("TestBot");
        var parser = new crawlercommons.robots.SimpleRobotRulesParser();
        var rules = parser.parseContent(
                "https://example.com/robots.txt",
                """
                        User-agent: *
                        Disallow: /blocked
                        """.getBytes(),
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
    void allowsWhenRobotsMissing() {
        RobotsService service = new RobotsService("TestBot");
        var allowAll = new crawlercommons.robots.SimpleRobotRules(
                crawlercommons.robots.SimpleRobotRules.RobotRulesMode.ALLOW_ALL
        );
        Object cachedRules = newCachedRules(allowAll, Instant.now());
        ConcurrentHashMap<String, Object> cache = new ConcurrentHashMap<>();
        cache.put("https://no-robots.example.com:443", cachedRules);
        ReflectionTestUtils.setField(service, "rulesCache", cache);

        assertThat(service.isAllowed("https://no-robots.example.com/anything")).isTrue();
    }

    private static Object newCachedRules(crawlercommons.robots.BaseRobotRules rules, Instant fetchedAt) {
        try {
            Class<?> cachedRulesClass =
                    Class.forName("com.factcheck.collector.integration.ingestion.RobotsService$CachedRules");
            var constructor = cachedRulesClass.getDeclaredConstructor(
                    crawlercommons.robots.BaseRobotRules.class,
                    Instant.class
            );
            constructor.setAccessible(true);
            return constructor.newInstance(rules, fetchedAt);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to construct cached rules for test", e);
        }
    }
}