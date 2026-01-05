package com.factcheck.collector.integration.fetcher;

import com.factcheck.collector.domain.entity.SourceEndpoint;
import com.factcheck.collector.domain.enums.SourceKind;
import com.factcheck.collector.exception.FetchException;
import com.factcheck.collector.integration.newsapi.NewsApiClient;
import com.factcheck.collector.integration.newsapi.dto.NewsApiEverythingResponse;
import com.factcheck.collector.integration.newsapi.NewsApiProperties;
import com.factcheck.collector.integration.newsapi.dto.NewsApiArticle;
import com.factcheck.collector.integration.newsapi.dto.NewsApiArticleSource;
import com.factcheck.collector.repository.ArticleSourceRepository;
import com.factcheck.collector.repository.SourceEndpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class NewsApiFetcher implements SourceFetcher, BatchResettableFetcher {

    private static final int MAX_PAGE_SIZE_LIMIT = 100;
    private static final int MAX_PAGES_LIMIT = 5;
    private static final int MAX_SOURCES_LIMIT = 20;
    private static final int MAX_REQUESTS_LIMIT = 100;

    private final NewsApiClient newsApiClient;
    private final NewsApiProperties properties;
    private final SourceEndpointRepository sourceEndpointRepository;
    private final ArticleSourceRepository articleSourceRepository;

    private final Object batchLock = new Object();
    private BatchCache batchCache;
    private boolean requestLimitReached;

    @Override
    public List<RawArticle> fetch(SourceEndpoint sourceEndpoint) throws FetchException {
        synchronized (batchLock) {
            if (batchCache == null || !batchCache.containsEndpoint(sourceEndpoint.getId())) {
                if (requestLimitReached) {
                    log.info("NewsAPI request limit reached, deferring endpoint id={}", sourceEndpoint.getId());
                    return List.of();
                }
                batchCache = buildBatch();
                if (batchCache == null || !batchCache.containsEndpoint(sourceEndpoint.getId())) {
                    if (requestLimitReached) {
                        log.info("NewsAPI request limit reached, deferring endpoint id={}", sourceEndpoint.getId());
                    }
                    return List.of();
                }
            }

            List<RawArticle> result = batchCache.consume(sourceEndpoint.getId());
            if (batchCache.isEmpty()) {
                batchCache = null;
            }
            return result;
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
        if (sourceEndpoint.getKind() != SourceKind.API) {
            return false;
        }
        String provider = sourceEndpoint.getApiProvider();
        return provider != null && provider.equalsIgnoreCase("newsapi");
    }

    private BatchCache buildBatch() throws FetchException {
        try {
            requestLimitReached = false;
            List<SourceEndpoint> endpoints = sourceEndpointRepository.findByEnabledTrueAndKind(SourceKind.API)
                    .stream()
                    .filter(this::supports)
                    .filter(endpoint -> endpoint.getApiQuery() != null && !endpoint.getApiQuery().isBlank())
                    .toList();

            if (endpoints.isEmpty()) {
                log.info("No enabled NewsAPI endpoints found");
                return new BatchCache(Map.of());
            }

            Map<Long, Long> articleCounts = loadArticleCounts(endpoints);
            List<SourceEndpoint> sorted = endpoints.stream()
                    .sorted((a, b) -> {
                        long countA = articleCounts.getOrDefault(a.getId(), 0L);
                        long countB = articleCounts.getOrDefault(b.getId(), 0L);
                        int cmp = Long.compare(countB, countA);
                        if (cmp != 0) {
                            return cmp;
                        }
                        return Long.compare(a.getId(), b.getId());
                    })
                    .toList();

            int maxSources = Math.min(MAX_SOURCES_LIMIT, Math.max(1, properties.getMaxSourcesPerRequest()));
            int maxPages = Math.min(MAX_PAGES_LIMIT, Math.max(1, properties.getMaxPagesPerBatch()));
            int maxRequests = Math.min(MAX_REQUESTS_LIMIT, Math.max(1, properties.getMaxRequestsPerIngestion()));
            int pageSize = Math.min(MAX_PAGE_SIZE_LIMIT, Math.max(1, properties.getPageSize()));
            if (properties.getMaxSourcesPerRequest() > MAX_SOURCES_LIMIT) {
                log.warn("NewsAPI max sources per request capped at {}", MAX_SOURCES_LIMIT);
            }
            if (properties.getMaxPagesPerBatch() > MAX_PAGES_LIMIT) {
                log.warn("NewsAPI max pages per batch capped at {}", MAX_PAGES_LIMIT);
            }
            if (properties.getMaxRequestsPerIngestion() > MAX_REQUESTS_LIMIT) {
                log.warn("NewsAPI max requests per ingestion capped at {}", MAX_REQUESTS_LIMIT);
            }
            if (properties.getPageSize() > MAX_PAGE_SIZE_LIMIT) {
                log.warn("NewsAPI page size capped at {}", MAX_PAGE_SIZE_LIMIT);
            }

            Map<String, SourceEndpoint> sourceIdToEndpoint = new HashMap<>();
            for (SourceEndpoint endpoint : sorted) {
                String sourceId = normalizeSourceId(endpoint.getApiQuery());
                if (sourceId == null) {
                    continue;
                }
                if (sourceIdToEndpoint.putIfAbsent(sourceId, endpoint) != null) {
                    log.warn("Duplicate NewsAPI source id {} for endpoint id={}", sourceId, endpoint.getId());
                }
            }

            Map<Long, List<RawArticle>> byEndpointId = new HashMap<>();

            int requestCount = 0;
            boolean limitReached = false;

            for (int i = 0; i < sorted.size(); i += maxSources) {
                List<SourceEndpoint> batch = sorted.subList(i, Math.min(i + maxSources, sorted.size()));
                List<String> sourceIds = batch.stream()
                        .map(SourceEndpoint::getApiQuery)
                        .map(this::normalizeSourceId)
                        .filter(id -> id != null && !id.isBlank())
                        .distinct()
                        .collect(Collectors.toList());

                if (sourceIds.isEmpty()) {
                    continue;
                }

                String sourcesParam = String.join(",", sourceIds);
                Set<String> batchSourceIdSet = new HashSet<>(sourceIds);
                List<Long> batchEndpointIds = batch.stream()
                        .map(SourceEndpoint::getId)
                        .toList();

                String sortBy = properties.getSortBy();
                if (sortBy == null || sortBy.isBlank()) {
                    sortBy = "publishedAt";
                }

                boolean batchInitialized = false;

                for (int page = 1; page <= maxPages; page++) {
                    if (requestCount >= maxRequests) {
                        limitReached = true;
                        break;
                    }
                    NewsApiEverythingResponse response = newsApiClient.fetchEverything(
                            sourcesParam,
                            sortBy,
                            page,
                            pageSize
                    );
                    requestCount++;
                    if (!batchInitialized) {
                        for (Long endpointId : batchEndpointIds) {
                            byEndpointId.putIfAbsent(endpointId, new ArrayList<>());
                        }
                        batchInitialized = true;
                    }
                    List<NewsApiArticle> articles = response.articles();
                    if (articles == null || articles.isEmpty()) {
                        break;
                    }

                    for (NewsApiArticle article : articles) {
                        NewsApiArticleSource source = article.source();
                        String sourceId = source != null ? normalizeSourceId(source.id()) : null;
                        if (sourceId == null || sourceId.isBlank()) {
                            continue;
                        }
                        if (!batchSourceIdSet.contains(sourceId)) {
                            continue;
                        }
                        SourceEndpoint endpoint = sourceIdToEndpoint.get(sourceId);
                        if (endpoint == null) {
                            continue;
                        }
                        String url = article.url();
                        String title = article.title();
                        if (url == null || url.isBlank() || title == null || title.isBlank()) {
                            continue;
                        }
                        Instant publishedAt = parsePublishedAt(article.publishedAt());
                        List<RawArticle> endpointArticles = byEndpointId.get(endpoint.getId());
                        if (endpointArticles == null) {
                            continue;
                        }
                        endpointArticles.add(RawArticle.builder()
                                .sourceItemId(url)
                                .externalUrl(url)
                                .title(title)
                                .description(article.description() != null ? article.description() : "")
                                .publishedDate(publishedAt)
                                .build());
                    }

                    if (articles.size() < pageSize) {
                        break;
                    }
                }
                if (limitReached) {
                    break;
                }
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
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (ArticleSourceRepository.SourceEndpointCount count : articleSourceRepository.countBySourceEndpointIds(ids)) {
            counts.put(count.getSourceEndpointId(), count.getArticleCount());
        }
        return counts;
    }

    private String normalizeSourceId(String sourceId) {
        if (sourceId == null) {
            return null;
        }
        String trimmed = sourceId.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static class BatchCache {
        private final Map<Long, List<RawArticle>> byEndpointId;
        private final Set<Long> remainingIds;

        private BatchCache(Map<Long, List<RawArticle>> byEndpointId) {
            this.byEndpointId = new HashMap<>(byEndpointId);
            this.remainingIds = new HashSet<>(byEndpointId.keySet());
        }

        private boolean containsEndpoint(Long endpointId) {
            return remainingIds.contains(endpointId);
        }

        private List<RawArticle> consume(Long endpointId) {
            remainingIds.remove(endpointId);
            return byEndpointId.getOrDefault(endpointId, List.of());
        }

        private boolean isEmpty() {
            return remainingIds.isEmpty();
        }
    }
}
