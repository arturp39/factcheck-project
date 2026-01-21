package com.factcheck.collector.integration.ingestion.fetcher;

import com.factcheck.collector.domain.entity.SourceEndpoint;
import com.factcheck.collector.domain.enums.SourceKind;
import com.factcheck.collector.exception.FetchException;
import com.factcheck.collector.exception.NewsApiRateLimitException;
import com.factcheck.collector.integration.catalog.newsapi.NewsApiClient;
import com.factcheck.collector.integration.catalog.newsapi.NewsApiProperties;
import com.factcheck.collector.integration.catalog.newsapi.dto.NewsApiArticle;
import com.factcheck.collector.integration.catalog.newsapi.dto.NewsApiArticleSource;
import com.factcheck.collector.integration.catalog.newsapi.dto.NewsApiEverythingResponse;
import com.factcheck.collector.repository.ArticleSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class NewsApiFetcher implements SourceFetcher, BatchResettableFetcher {

    private static final String PROVIDER = "newsapi";

    private static final int NEWSAPI_DEFAULT_PAGE_SIZE = 100;
    private static final int MAX_PAGES_LIMIT = 5;
    private static final int MAX_SOURCES_LIMIT = 20;
    private static final int MAX_REQUESTS_LIMIT = 100;

    private final NewsApiClient newsApiClient;
    private final NewsApiProperties properties;
    private final com.factcheck.collector.repository.SourceEndpointRepository sourceEndpointRepository;
    private final ArticleSourceRepository articleSourceRepository;

    private final Object batchLock = new Object();
    private BatchCache batchCache;
    private boolean requestLimitReached;

    @Override
    public List<RawArticle> fetch(SourceEndpoint sourceEndpoint) throws FetchException {
        if (sourceEndpoint == null || sourceEndpoint.getId() == null) {
            return List.of();
        }

        synchronized (batchLock) {
            if (batchCache != null) {
                if (batchCache.isKnownEndpoint(sourceEndpoint.getId())) {
                    List<RawArticle> out = batchCache.consumeOnce(sourceEndpoint.getId());
                    if (batchCache.isEmpty()) {
                        batchCache = null;
                    }
                    return out;
                }
            }

            if (requestLimitReached) {
                log.info("NewsAPI request limit reached, deferring endpoint id={}", sourceEndpoint.getId());
                return List.of();
            }

            batchCache = buildBatch();

            if (!batchCache.isKnownEndpoint(sourceEndpoint.getId())) {
                // Endpoint not included in this batch.
                if (requestLimitReached) {
                    log.info("NewsAPI request limit reached, deferring endpoint id={}", sourceEndpoint.getId());
                }
                // Allow rebuild when the batch is empty.
                if (batchCache != null && batchCache.isEmpty()) {
                    batchCache = null;
                }
                return List.of();
            }

            List<RawArticle> out = batchCache.consumeOnce(sourceEndpoint.getId());
            if (batchCache.isEmpty()) {
                batchCache = null;
            }
            return out;
        }
    }

    @Override
    public void resetBatch() {
        synchronized (batchLock) {
            batchCache = null;
            requestLimitReached = false;
        }
    }

    @Override
    public boolean supports(SourceEndpoint sourceEndpoint) {
        if (sourceEndpoint == null) return false;
        if (sourceEndpoint.getKind() != SourceKind.API) return false;
        String provider = sourceEndpoint.getApiProvider();
        return provider != null && provider.equalsIgnoreCase(PROVIDER);
    }

    private BatchCache buildBatch() throws FetchException {
        try {
            requestLimitReached = false;

            List<SourceEndpoint> endpoints = sourceEndpointRepository.findByEnabledTrueAndKind(SourceKind.API)
                    .stream()
                    .filter(this::supports)
                    .filter(ep -> ep.getApiQuery() != null && !ep.getApiQuery().isBlank())
                    .toList();

            if (endpoints.isEmpty()) {
                log.info("No enabled NewsAPI endpoints found");
                return new BatchCache(Map.of());
            }

            // Apply hard caps to batching params.
            int maxSources = cap("maxSourcesPerRequest", properties.getMaxSourcesPerRequest(), MAX_SOURCES_LIMIT);
            int maxPages = cap("maxPagesPerBatch", properties.getMaxPagesPerBatch(), MAX_PAGES_LIMIT);
            int maxRequests = cap("maxRequestsPerIngestion", properties.getMaxRequestsPerIngestion(), MAX_REQUESTS_LIMIT);

            String sortBy = properties.getSortBy();
            if (sortBy == null || sortBy.isBlank()) sortBy = "publishedAt";

            // Prioritize endpoints with higher existing volume.
            Map<Long, Long> articleCounts = loadArticleCounts(endpoints);

            List<SourceEndpoint> sorted = endpoints.stream()
                    .sorted((a, b) -> {
                        long countA = articleCounts.getOrDefault(a.getId(), 0L);
                        long countB = articleCounts.getOrDefault(b.getId(), 0L);
                        int cmp = Long.compare(countB, countA);
                        if (cmp != 0) return cmp;
                        return Long.compare(a.getId(), b.getId());
                    })
                    .toList();

            // Map normalized sourceId to endpoints.
            Map<String, SourceEndpoint> sourceIdToEndpoint = new HashMap<>();
            for (SourceEndpoint endpoint : sorted) {
                String sourceId = normalizeSourceId(endpoint.getApiQuery());
                if (sourceId == null) continue;

                SourceEndpoint prev = sourceIdToEndpoint.putIfAbsent(sourceId, endpoint);
                if (prev != null) {
                    log.warn("Duplicate NewsAPI source id {} for endpoint ids={} and {}. Using first.",
                            sourceId, prev.getId(), endpoint.getId());
                }
            }

            Map<Long, List<RawArticle>> byEndpointId = new HashMap<>();

            int requestCount = 0;
            boolean limitReached = false;

            // Batch by maxSources endpoints per call.
            for (int i = 0; i < sorted.size(); i += maxSources) {
                List<SourceEndpoint> batch = sorted.subList(i, Math.min(i + maxSources, sorted.size()));

                List<String> sourceIds = batch.stream()
                        .map(SourceEndpoint::getApiQuery)
                        .map(this::normalizeSourceId)
                        .filter(id -> id != null && !id.isBlank())
                        .distinct()
                        .toList();

                if (sourceIds.isEmpty()) {
                    continue;
                }

                String sourcesParam = String.join(",", sourceIds);
                Set<String> batchSourceIdSet = new HashSet<>(sourceIds);

                List<Long> batchEndpointIds = batch.stream()
                        .map(SourceEndpoint::getId)
                        .filter(Objects::nonNull)
                        .toList();

                // Init per-endpoint lists and URL sets.
                Map<Long, Set<String>> seenUrlsByEndpoint = new HashMap<>();
                for (Long endpointId : batchEndpointIds) {
                    byEndpointId.putIfAbsent(endpointId, new ArrayList<>());
                    seenUrlsByEndpoint.putIfAbsent(endpointId, new HashSet<>());
                }

                for (int page = 1; page <= maxPages; page++) {
                    if (requestCount >= maxRequests) {
                        limitReached = true;
                        break;
                    }

                    NewsApiEverythingResponse response;
                    try {
                        response = newsApiClient.fetchEverything(
                                sourcesParam,
                                sortBy,
                                page
                        );
                    } catch (NewsApiRateLimitException rateLimit) {
                        limitReached = true;
                        requestLimitReached = true;
                        Integer retryAfterSeconds = rateLimit.getRetryAfterSeconds();
                        if (retryAfterSeconds != null) {
                            log.warn("NewsAPI rate limit reached; retry-after={}s", retryAfterSeconds);
                        } else {
                            log.warn("NewsAPI rate limit reached");
                        }
                        break;
                    }
                    requestCount++;

                    List<NewsApiArticle> articles = response.articles();
                    if (articles == null || articles.isEmpty()) {
                        break;
                    }

                    for (NewsApiArticle article : articles) {
                        NewsApiArticleSource src = article.source();
                        String sourceId = (src != null) ? normalizeSourceId(src.id()) : null;
                        if (sourceId == null || sourceId.isBlank()) continue;
                        if (!batchSourceIdSet.contains(sourceId)) continue;

                        SourceEndpoint endpoint = sourceIdToEndpoint.get(sourceId);
                        if (endpoint == null || endpoint.getId() == null) continue;

                        String url = article.url();
                        String title = article.title();
                        if (url == null || url.isBlank() || title == null || title.isBlank()) continue;

                        // Deduplicate URLs per endpoint across pages.
                        Set<String> seen = seenUrlsByEndpoint.get(endpoint.getId());
                        if (seen == null) continue;
                        if (!seen.add(url)) continue;

                        Instant publishedAt = parsePublishedAt(article.publishedAt());

                        byEndpointId.get(endpoint.getId()).add(RawArticle.builder()
                                .sourceItemId(url)
                                .externalUrl(url)
                                .title(title)
                                .description(article.description() != null ? article.description() : "")
                                .publishedDate(publishedAt)
                                .build());
                    }

                    // Short page means we're done.
                    if (articles.size() < NEWSAPI_DEFAULT_PAGE_SIZE) {
                        break;
                    }
                }

                if (limitReached) break;
            }

            if (limitReached) {
                log.warn("NewsAPI request limit reached ({}/{}) for this ingestion", requestCount, maxRequests);
            }

            requestLimitReached = limitReached;
            return new BatchCache(byEndpointId);
        } catch (Exception e) {
            throw new FetchException("Failed to fetch NewsAPI articles", e);
        }
    }

    private int cap(String name, int configured, int hardMax) {
        int normalized = Math.max(1, configured);
        int capped = Math.min(hardMax, normalized);
        if (configured > hardMax) {
            log.warn("NewsAPI {} capped at {}", name, hardMax);
        }
        return capped;
    }

    private Instant parsePublishedAt(String publishedAt) {
        if (publishedAt == null || publishedAt.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(publishedAt);
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<Long, Long> loadArticleCounts(List<SourceEndpoint> endpoints) {
        List<Long> ids = endpoints.stream()
                .map(SourceEndpoint::getId)
                .filter(Objects::nonNull)
                .toList();

        if (ids.isEmpty()) {
            return Map.of();
        }

        Map<Long, Long> counts = new HashMap<>();
        for (ArticleSourceRepository.SourceEndpointCount c : articleSourceRepository.countBySourceEndpointIds(ids)) {
            if (c == null) continue;
            counts.put(c.getSourceEndpointId(), c.getArticleCount());
        }
        return counts;
    }

    private String normalizeSourceId(String sourceId) {
        if (sourceId == null) return null;
        String trimmed = sourceId.trim();
        if (trimmed.isBlank()) return null;
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static class BatchCache {
        private final Map<Long, List<RawArticle>> byEndpointId;
        private final Set<Long> knownIds;
        private final Set<Long> remainingIds;

        private BatchCache(Map<Long, List<RawArticle>> byEndpointId) {
            this.byEndpointId = new HashMap<>(byEndpointId);
            this.knownIds = new HashSet<>(byEndpointId.keySet());
            this.remainingIds = new HashSet<>(byEndpointId.keySet());
        }

        boolean isKnownEndpoint(Long endpointId) {
            return knownIds.contains(endpointId);
        }

        List<RawArticle> consumeOnce(Long endpointId) {
            // Do not rebuild when already consumed.
            if (!remainingIds.remove(endpointId)) {
                return List.of();
            }
            return byEndpointId.getOrDefault(endpointId, List.of());
        }

        boolean isEmpty() {
            return remainingIds.isEmpty();
        }
    }
}
