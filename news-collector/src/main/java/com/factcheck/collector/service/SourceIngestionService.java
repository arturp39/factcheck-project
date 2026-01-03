package com.factcheck.collector.service;

import com.factcheck.collector.domain.entity.Article;
import com.factcheck.collector.domain.entity.ArticleSource;
import com.factcheck.collector.domain.entity.IngestionLog;
import com.factcheck.collector.domain.entity.Publisher;
import com.factcheck.collector.domain.entity.SourceEndpoint;
import com.factcheck.collector.domain.enums.ArticleStatus;
import com.factcheck.collector.domain.enums.IngestionStatus;
import com.factcheck.collector.exception.FetchException;
import com.factcheck.collector.exception.ProcessingFailedException;
import com.factcheck.collector.integration.fetcher.RawArticle;
import com.factcheck.collector.integration.fetcher.SourceFetcher;
import com.factcheck.collector.repository.ArticleRepository;
import com.factcheck.collector.repository.ArticleSourceRepository;
import com.factcheck.collector.repository.IngestionLogRepository;
import com.factcheck.collector.repository.SourceEndpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SourceIngestionService {

    private final ArticleSourceRepository articleSourceRepository;
    private final SourceEndpointRepository sourceEndpointRepository;
    private final ArticleRepository articleRepository;
    private final IngestionLogRepository ingestionLogRepository;
    private final List<SourceFetcher> fetchers;
    private final ArticleProcessingService articleProcessingService;
    private final EmbeddingService embeddingService;
    private final WeaviateIndexingService weaviateIndexingService;

    public void ingestSingleSource(SourceEndpoint sourceEndpoint, String correlationId) {

        log.info("Ingesting endpoint id={} name={} publisher={} correlationId={}",
                sourceEndpoint.getId(),
                sourceEndpoint.getDisplayName(),
                sourceEndpoint.getPublisher().getName(),
                correlationId);

        IngestionLog logEntry = IngestionLog.builder()
                .sourceEndpoint(sourceEndpoint)
                .status(IngestionStatus.RUNNING)
                .correlationId(correlationId)
                .startedAt(Instant.now())
                .build();
        ingestionLogRepository.save(logEntry);

        int fetched = 0;
        int processed = 0;
        int failed = 0;

        try {
            // Choose fetcher implementation per source type (RSS, sitemap, robots, etc.)
            SourceFetcher fetcher = fetchers.stream()
                    .filter(f -> f.supports(sourceEndpoint.getKind()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No fetcher for type " + sourceEndpoint.getKind()));

            List<RawArticle> rawArticles = fetcher.fetch(sourceEndpoint);
            fetched = rawArticles.size();

            for (RawArticle raw : rawArticles) {
                final String url = raw.getExternalUrl();

                try {
                    // Skip pages that are likely videos/galleries because downstream expects text
                    if (isNonTextMediaPage(url)) {
                        log.info("Skipping non-text media page: {}", url);
                        continue;
                    }

                    String fullText = raw.getRawText();
                    if (fullText == null || fullText.isBlank()) {
                        log.info("Skipping article with no extracted text: {}", url);
                        continue;
                    }

                    String sourceItemId = raw.getSourceItemId();
                    if (sourceItemId == null || sourceItemId.isBlank()) {
                        sourceItemId = url;
                    }
                    if (sourceItemId == null || sourceItemId.isBlank()) {
                        log.info("Skipping article with no source item id: {}", url);
                        continue;
                    }

                    if (articleSourceRepository.existsBySourceEndpointAndSourceItemId(sourceEndpoint, sourceItemId)) {
                        log.debug("Article source already exists, skipping sourceItemId={}", sourceItemId);
                        continue;
                    }

                    String canonicalUrl = url;
                    if (canonicalUrl == null || canonicalUrl.isBlank()) {
                        log.info("Skipping article with no canonical url: {}", url);
                        continue;
                    }
                    String canonicalHash = sha1Hex(canonicalUrl);

                    Publisher publisher = sourceEndpoint.getPublisher();
                    Article article = articleRepository.findByPublisherAndCanonicalUrlHash(publisher, canonicalHash)
                            .orElse(null);

                    boolean isNew = false;
                    if (article == null) {
                        article = Article.builder()
                                .publisher(publisher)
                                .canonicalUrl(canonicalUrl)
                                .canonicalUrlHash(canonicalHash)
                                .title(raw.getTitle())
                                .description(raw.getDescription())
                                .publishedDate(raw.getPublishedDate())
                                .status(ArticleStatus.PENDING)
                                .build();

                        try {
                            article = articleRepository.save(article);
                            isNew = true;
                        } catch (DataIntegrityViolationException ex) {
                            log.info("Duplicate article detected at DB level, skipping url={}", url);
                            continue;
                        }
                    } else {
                        article.setLastSeenAt(Instant.now());
                        articleRepository.save(article);
                    }

                    ArticleSource link = ArticleSource.builder()
                            .article(article)
                            .sourceEndpoint(sourceEndpoint)
                            .sourceItemId(sourceItemId)
                            .build();

                    try {
                        articleSourceRepository.save(link);
                    } catch (DataIntegrityViolationException ex) {
                        log.debug("Duplicate article source link, skipping sourceItemId={}", sourceItemId);
                    }

                    if (!isNew) {
                        continue;
                    }

                    // chunk -> embed -> push to Weaviate
                    processAndIndexArticle(article, fullText, correlationId);
                    processed++;

                } catch (ProcessingFailedException e) {
                    failed++;
                    log.warn("Processing failed for article url={}", url, e);

                } catch (Exception e) {
                    failed++;
                    log.error("Unexpected error processing article url={}", url, e);
                }
            }

            logEntry.setStatus(
                    failed == 0 ? IngestionStatus.SUCCESS :
                            (processed > 0 ? IngestionStatus.PARTIAL : IngestionStatus.FAILED)
            );

            logEntry.setArticlesFetched(fetched);
            logEntry.setArticlesProcessed(processed);
            logEntry.setArticlesFailed(failed);
            logEntry.setCompletedAt(Instant.now());

            if (failed == 0) {
                sourceEndpoint.setLastSuccessAt(Instant.now());
                sourceEndpoint.setFailureCount(0);
            } else {
                sourceEndpoint.setFailureCount(sourceEndpoint.getFailureCount() + 1);
            }

            sourceEndpoint.setLastFetchedAt(Instant.now());
            sourceEndpointRepository.save(sourceEndpoint);

        } catch (FetchException fetchEx) {
            log.error("Failed to fetch endpoint id={} name={}", sourceEndpoint.getId(), sourceEndpoint.getDisplayName(), fetchEx);
            logEntry.setStatus(IngestionStatus.FAILED);
            logEntry.setErrorDetails("Fetch error: " + fetchEx.getMessage());
            logEntry.setCompletedAt(Instant.now());
        }

        ingestionLogRepository.save(logEntry);
    }

    private void processAndIndexArticle(Article article, String fullText, String correlationId) {
        article.setStatus(ArticleStatus.PROCESSING);
        articleRepository.save(article);

        try {
            var chunks = articleProcessingService.createChunks(article, fullText, correlationId);
            var embeddings = embeddingService.embedChunks(chunks, correlationId);
            weaviateIndexingService.indexArticleChunks(article, chunks, embeddings, correlationId);

            article.setChunkCount(chunks.size());
            article.setWeaviateIndexed(true);
            article.setStatus(ArticleStatus.PROCESSED);
            articleRepository.save(article);

        } catch (Exception e) {
            log.error("Processing/indexing failed for article id={}", article.getId(), e);
            article.setStatus(ArticleStatus.FAILED);
            article.setErrorMessage(e.getMessage());
            articleRepository.save(article);
            throw new ProcessingFailedException(e);
        }
    }

    private boolean isNonTextMediaPage(String url) {
        if (url == null) {
            return false;
        }
        String u = url.toLowerCase();

        return u.contains("/video/")
                || u.contains("/videos/")
                || u.contains("/newsfeed/")
                || u.contains("/latest-news-bulletin")
                || u.contains("/picture/")
                || u.contains("/cartoon/")
                || u.contains("/gallery/")
                || u.contains("/slideshow/")
                || u.contains("/watch/")
                || u.contains("/live/")
                || u.contains("/iplayer/");
    }

    private String sha1Hex(String value) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute canonical url hash", e);
        }
    }
}
