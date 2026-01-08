package com.factcheck.collector.service.ingestion;

import com.factcheck.collector.domain.entity.IngestionLog;
import com.factcheck.collector.domain.entity.IngestionRun;
import com.factcheck.collector.domain.entity.SourceEndpoint;
import com.factcheck.collector.domain.enums.IngestionStatus;
import com.factcheck.collector.exception.FetchException;
import com.factcheck.collector.integration.ingestion.RobotsService;
import com.factcheck.collector.integration.ingestion.fetcher.ArticleFetchResult;
import com.factcheck.collector.integration.ingestion.fetcher.BatchResettableFetcher;
import com.factcheck.collector.integration.ingestion.fetcher.RawArticle;
import com.factcheck.collector.integration.ingestion.fetcher.SourceFetcher;
import com.factcheck.collector.repository.IngestionLogRepository;
import com.factcheck.collector.repository.SourceEndpointRepository;
import com.factcheck.collector.service.ingestion.pipeline.ArticleDiscoveryService;
import com.factcheck.collector.service.ingestion.pipeline.ArticleEnrichmentService;
import com.factcheck.collector.service.ingestion.pipeline.ArticleIndexingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EndpointIngestionJob {

    private static final String BLOCK_REASON_ROBOTS = "ROBOTS_DISALLOWED";
    private static final String BLOCK_REASON_BLOCKED = "BLOCKED_OR_CAPTCHA";
    private static final String BLOCK_REASON_EXTRACTION = "EXTRACTION_FAILED";

    private final SourceEndpointRepository sourceEndpointRepository;
    private final IngestionLogRepository ingestionLogRepository;
    private final List<SourceFetcher> fetchers;
    private final ArticleDiscoveryService articleDiscoveryService;
    private final ArticleEnrichmentService articleEnrichmentService;
    private final ArticleIndexingService articleIndexingService;
    private final RobotsService robotsService;

    @Value("${ingestion.block-threshold:2}")
    private int blockThreshold;

    @Value("${ingestion.block-duration:PT24H}")
    private Duration blockDuration;

    public IngestionStatus ingestSingleSource(
            SourceEndpoint sourceEndpoint,
            UUID correlationId,
            IngestionRun run,
            IngestionLog logEntry
    ) {
        if (logEntry == null) {
            throw new IllegalArgumentException("logEntry is required");
        }
        String correlationIdStr = correlationId.toString();

        log.info("Ingesting endpoint id={} name={} publisher={} correlationId={}",
                sourceEndpoint.getId(),
                sourceEndpoint.getDisplayName(),
                sourceEndpoint.getPublisher().getName(),
                correlationIdStr);

        // Keep PROCESSING to prevent duplicate claims.
        logEntry.setStatus(IngestionStatus.PROCESSING);

        if (logEntry.getCorrelationId() == null) {
            logEntry.setCorrelationId(correlationId);
        }
        if (logEntry.getStartedAt() == null) {
            logEntry.setStartedAt(Instant.now());
        }

        sourceEndpoint.setLastAttemptedAt(Instant.now());
        sourceEndpointRepository.save(sourceEndpoint);

        int fetched = 0;
        int processed = 0;
        int failed = 0;
        IngestionStatus finalStatus = null;
        String errorDetails = null;
        List<RawArticle> rawArticles = List.of();

        try {
            if (sourceEndpoint.isRobotsDisallowed()) {
                errorDetails = "Robots.txt disallows scraping for this source";
                finalStatus = IngestionStatus.SKIPPED;
            }
            if (finalStatus == null && isBlocked(sourceEndpoint)) {
                errorDetails = "Source blocked until " + sourceEndpoint.getBlockedUntil();
                finalStatus = IngestionStatus.SKIPPED;
            }

            if (finalStatus == null) {
                SourceFetcher fetcher = fetchers.stream()
                        .filter(f -> f.supports(sourceEndpoint))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No fetcher for type " + sourceEndpoint.getKind()));

                rawArticles = fetcher.fetch(sourceEndpoint);
                fetched = rawArticles.size();

                String sampleUrl = rawArticles.stream()
                        .map(RawArticle::getExternalUrl)
                        .filter(u -> u != null && !u.isBlank())
                        .findFirst()
                        .orElse(null);
                if (sampleUrl != null && !robotsService.isAllowed(sampleUrl)) {
                    markRobotsDisallowed(sourceEndpoint);
                    sourceEndpoint.setLastFetchedAt(Instant.now());
                    sourceEndpointRepository.save(sourceEndpoint);
                    errorDetails = "Robots.txt disallows scraping for " + sampleUrl;
                    finalStatus = IngestionStatus.SKIPPED;
                }
            }

            if (finalStatus == null) {
                boolean blockSignalDetected = false;
                String blockReason = null;
                boolean hadSuccess = false;

                for (RawArticle raw : rawArticles) {
                    final String url = raw.getExternalUrl();
                    try {
                        if (articleDiscoveryService.shouldSkip(raw)) {
                            continue;
                        }

                        ArticleDiscoveryService.DiscoveryResult discovery =
                                articleDiscoveryService.discover(sourceEndpoint, raw);
                        if (discovery == null) {
                            continue;
                        }

                        if (!discovery.isNew()) {
                            continue;
                        }

                        ArticleEnrichmentService.EnrichmentResult enrichment =
                                articleEnrichmentService.enrich(discovery.article(), raw);
                        if (!enrichment.success()) {
                            failed++;
                            String reason = classifyBlockReason(enrichment.fetchResult());
                            if (reason != null) {
                                blockSignalDetected = true;
                                blockReason = reason;
                                if (BLOCK_REASON_ROBOTS.equals(reason)) {
                                    markRobotsDisallowed(sourceEndpoint);
                                    break;
                                }
                                if (BLOCK_REASON_BLOCKED.equals(reason)) {
                                    break;
                                }
                            }
                            continue;
                        }

                        hadSuccess = true;
                        if (articleIndexingService.index(discovery.article(), enrichment.extractedText(), correlationIdStr)) {
                            processed++;
                        } else {
                            failed++;
                        }
                    } catch (Exception e) {
                        failed++;
                        log.error("Unexpected error processing article url={}", url, e);
                    }
                }

                finalStatus = failed == 0 ? IngestionStatus.SUCCESS :
                        (processed > 0 ? IngestionStatus.PARTIAL : IngestionStatus.FAILED);

                boolean success = finalStatus == IngestionStatus.SUCCESS
                        || finalStatus == IngestionStatus.PARTIAL;

                if (blockSignalDetected && !BLOCK_REASON_ROBOTS.equals(blockReason)) {
                    if (BLOCK_REASON_BLOCKED.equals(blockReason)
                            || (!hadSuccess && BLOCK_REASON_EXTRACTION.equals(blockReason))) {
                        applyBlockSignal(sourceEndpoint, blockReason);
                    }
                } else if (hadSuccess) {
                    clearBlockState(sourceEndpoint);
                }

                if (failed == 0) {
                    sourceEndpoint.setLastSuccessAt(Instant.now());
                    sourceEndpoint.setFailureCount(0);
                } else {
                    sourceEndpoint.setFailureCount(sourceEndpoint.getFailureCount() + 1);
                }

                if (success) {
                    sourceEndpoint.setLastFetchedAt(Instant.now());
                }
                sourceEndpointRepository.save(sourceEndpoint);
            }

        } catch (FetchException fetchEx) {
            log.error("Failed to fetch endpoint id={} name={}",
                    sourceEndpoint.getId(), sourceEndpoint.getDisplayName(), fetchEx);
            finalStatus = IngestionStatus.FAILED;
            errorDetails = "Fetch error: " + fetchEx.getMessage();
            sourceEndpoint.setFailureCount(sourceEndpoint.getFailureCount() + 1);
            sourceEndpointRepository.save(sourceEndpoint);
        }

        if (finalStatus == null) {
            finalStatus = IngestionStatus.FAILED;
        }

        logEntry.setStatus(finalStatus);
        if (errorDetails != null) {
            logEntry.setErrorDetails(errorDetails);
        }
        logEntry.setArticlesFetched(fetched);
        logEntry.setArticlesProcessed(processed);
        logEntry.setArticlesFailed(failed);
        logEntry.setCompletedAt(Instant.now());
        ingestionLogRepository.save(logEntry);

        return finalStatus;
    }

    public void resetBatchCaches() {
        for (SourceFetcher fetcher : fetchers) {
            if (fetcher instanceof BatchResettableFetcher resettableFetcher) {
                resettableFetcher.resetBatch();
            }
        }
    }

    private boolean isBlocked(SourceEndpoint sourceEndpoint) {
        Instant blockedUntil = sourceEndpoint.getBlockedUntil();
        return blockedUntil != null && blockedUntil.isAfter(Instant.now());
    }

    private void markRobotsDisallowed(SourceEndpoint sourceEndpoint) {
        sourceEndpoint.setRobotsDisallowed(true);
        sourceEndpoint.setBlockedUntil(null);
        sourceEndpoint.setBlockReason(BLOCK_REASON_ROBOTS);
        sourceEndpoint.setBlockCount(0);
    }

    private String classifyBlockReason(ArticleFetchResult fetchResult) {
        if (fetchResult == null) {
            return null;
        }
        if (Boolean.TRUE.equals(fetchResult.getBlockedSuspected())) {
            return BLOCK_REASON_BLOCKED;
        }
        String fetchError = fetchResult.getFetchError();
        if (fetchError != null && fetchError.toLowerCase(Locale.ROOT).contains("robots.txt")) {
            return BLOCK_REASON_ROBOTS;
        }
        String extractionError = fetchResult.getExtractionError();
        if (extractionError != null && extractionError.toLowerCase(Locale.ROOT).contains("low-quality extraction")) {
            return BLOCK_REASON_EXTRACTION;
        }
        return null;
    }

    private void applyBlockSignal(SourceEndpoint sourceEndpoint, String reason) {
        int nextCount = sourceEndpoint.getBlockCount() + 1;
        sourceEndpoint.setBlockCount(nextCount);
        sourceEndpoint.setBlockReason(reason);
        if (nextCount >= blockThreshold) {
            sourceEndpoint.setBlockedUntil(Instant.now().plus(blockDuration));
        }
    }

    private void clearBlockState(SourceEndpoint sourceEndpoint) {
        sourceEndpoint.setBlockCount(0);
        sourceEndpoint.setBlockReason(null);
        sourceEndpoint.setBlockedUntil(null);
    }
}