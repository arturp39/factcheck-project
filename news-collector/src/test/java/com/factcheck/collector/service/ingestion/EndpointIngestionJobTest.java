package com.factcheck.collector.service.ingestion;

import com.factcheck.collector.domain.entity.*;
import com.factcheck.collector.domain.enums.IngestionStatus;
import com.factcheck.collector.domain.enums.SourceKind;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EndpointIngestionJobTest {

    @Mock
    private SourceEndpointRepository sourceEndpointRepository;

    @Mock
    private IngestionLogRepository ingestionLogRepository;

    @Mock
    private SourceFetcher fetcher;

    @Mock
    private ArticleDiscoveryService articleDiscoveryService;

    @Mock
    private ArticleEnrichmentService articleEnrichmentService;

    @Mock
    private ArticleIndexingService articleIndexingService;

    @Mock
    private RobotsService robotsService;

    private SourceEndpoint sourceEndpoint;

    @BeforeEach
    void initSource() {
        Publisher publisher = Publisher.builder()
                .id(100L)
                .name("Demo Publisher")
                .build();

        sourceEndpoint = SourceEndpoint.builder()
                .id(7L)
                .publisher(publisher)
                .kind(SourceKind.RSS)
                .displayName("RSS Source")
                .rssUrl("https://example.com/rss")
                .failureCount(0)
                .blockCount(0)
                .build();
    }

    @Test
    void ingestSingleSource_throwsWhenLogEntryNull() {
        EndpointIngestionJob job = new EndpointIngestionJob(
                sourceEndpointRepository,
                ingestionLogRepository,
                List.of(fetcher),
                articleDiscoveryService,
                articleEnrichmentService,
                articleIndexingService,
                robotsService
        );

        assertThatThrownBy(() -> job.ingestSingleSource(sourceEndpoint, UUID.randomUUID(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("logEntry is required");

        verifyNoInteractions(sourceEndpointRepository, ingestionLogRepository);
    }

    @Test
    void ingestSingleSource_recordsFetchFailure() {
        when(fetcher.supports(sourceEndpoint)).thenReturn(true);
        when(fetcher.fetch(sourceEndpoint)).thenThrow(new FetchException("boom"));

        EndpointIngestionJob ingestionService = new EndpointIngestionJob(
                sourceEndpointRepository,
                ingestionLogRepository,
                List.of(fetcher),
                articleDiscoveryService,
                articleEnrichmentService,
                articleIndexingService,
                robotsService
        );

        UUID correlationId = UUID.randomUUID();
        IngestionLog logEntry = IngestionLog.builder()
                .sourceEndpoint(sourceEndpoint)
                .correlationId(correlationId)
                .build();

        ingestionService.ingestSingleSource(sourceEndpoint, correlationId, null, logEntry);

        ArgumentCaptor<IngestionLog> logCaptor = ArgumentCaptor.forClass(IngestionLog.class);
        verify(ingestionLogRepository, atLeastOnce()).save(logCaptor.capture());

        IngestionLog finalLog = logCaptor.getValue();
        assertThat(finalLog.getStatus()).isEqualTo(IngestionStatus.FAILED);
        assertThat(finalLog.getErrorDetails()).contains("Fetch error: boom");
        assertThat(finalLog.getCorrelationId()).isEqualTo(correlationId);
        assertThat(finalLog.getCompletedAt()).isNotNull();

        verifyNoInteractions(articleDiscoveryService, articleEnrichmentService, articleIndexingService);

        ArgumentCaptor<SourceEndpoint> endpointCaptor = ArgumentCaptor.forClass(SourceEndpoint.class);
        verify(sourceEndpointRepository, atLeastOnce()).save(endpointCaptor.capture());
        SourceEndpoint saved = endpointCaptor.getValue();
        assertThat(saved.getFailureCount()).isEqualTo(1);
    }

    @Test
    void ingestSingleSource_throwsWhenNoFetcherSupportsEndpoint() {
        when(fetcher.supports(sourceEndpoint)).thenReturn(false);

        EndpointIngestionJob job = new EndpointIngestionJob(
                sourceEndpointRepository,
                ingestionLogRepository,
                List.of(fetcher),
                articleDiscoveryService,
                articleEnrichmentService,
                articleIndexingService,
                robotsService
        );

        UUID correlationId = UUID.randomUUID();
        IngestionLog logEntry = IngestionLog.builder()
                .sourceEndpoint(sourceEndpoint)
                .correlationId(correlationId)
                .build();

        assertThatThrownBy(() -> job.ingestSingleSource(sourceEndpoint, correlationId, null, logEntry))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No fetcher for type");

        verify(fetcher, never()).fetch(any());
        verifyNoInteractions(articleDiscoveryService, articleEnrichmentService, articleIndexingService);

        verify(sourceEndpointRepository, atLeastOnce()).save(sourceEndpoint);
    }


    @Test
    void ingestSingleSource_handlesSkipAndNotNewBranches() {
        when(fetcher.supports(sourceEndpoint)).thenReturn(true);

        RawArticle skip = RawArticle.builder().externalUrl("https://example.com/skip").sourceItemId("s").build();
        RawArticle notNew = RawArticle.builder().externalUrl("https://example.com/old").sourceItemId("o").build();
        when(fetcher.fetch(sourceEndpoint)).thenReturn(List.of(skip, notNew));

        when(robotsService.isAllowed("https://example.com/skip")).thenReturn(true);

        when(articleDiscoveryService.shouldSkip(skip)).thenReturn(true);
        when(articleDiscoveryService.shouldSkip(notNew)).thenReturn(false);

        var article = Article.builder().id(1L).build();
        when(articleDiscoveryService.discover(sourceEndpoint, notNew))
                .thenReturn(new ArticleDiscoveryService.DiscoveryResult(article, false));

        EndpointIngestionJob job = new EndpointIngestionJob(
                sourceEndpointRepository,
                ingestionLogRepository,
                List.of(fetcher),
                articleDiscoveryService,
                articleEnrichmentService,
                articleIndexingService,
                robotsService
        );

        UUID correlationId = UUID.randomUUID();
        IngestionLog logEntry = IngestionLog.builder().sourceEndpoint(sourceEndpoint).correlationId(correlationId).build();

        IngestionStatus status = job.ingestSingleSource(sourceEndpoint, correlationId, null, logEntry);

        assertThat(status).isEqualTo(IngestionStatus.SUCCESS);

        verify(articleEnrichmentService, never()).enrich(any(), any());
        verify(articleIndexingService, never()).index(any(), anyString(), anyString());
    }

    @Test
    void ingestSingleSource_handlesDiscoverReturningNull() {
        when(fetcher.supports(sourceEndpoint)).thenReturn(true);

        RawArticle raw = RawArticle.builder().externalUrl("https://example.com/x").sourceItemId("x").build();
        when(fetcher.fetch(sourceEndpoint)).thenReturn(List.of(raw));

        when(robotsService.isAllowed("https://example.com/x")).thenReturn(true);
        when(articleDiscoveryService.shouldSkip(raw)).thenReturn(false);
        when(articleDiscoveryService.discover(sourceEndpoint, raw)).thenReturn(null);

        EndpointIngestionJob job = new EndpointIngestionJob(
                sourceEndpointRepository,
                ingestionLogRepository,
                List.of(fetcher),
                articleDiscoveryService,
                articleEnrichmentService,
                articleIndexingService,
                robotsService
        );

        IngestionStatus status = job.ingestSingleSource(
                sourceEndpoint,
                UUID.randomUUID(),
                null,
                IngestionLog.builder().sourceEndpoint(sourceEndpoint).build()
        );

        assertThat(status).isEqualTo(IngestionStatus.SUCCESS);
        verifyNoInteractions(articleEnrichmentService, articleIndexingService);
    }

    @Test
    void ingestSkipsWhenRobotsAlreadyDisallowed() {
        sourceEndpoint.setRobotsDisallowed(true);

        EndpointIngestionJob job = new EndpointIngestionJob(
                sourceEndpointRepository,
                ingestionLogRepository,
                List.of(fetcher),
                articleDiscoveryService,
                articleEnrichmentService,
                articleIndexingService,
                robotsService
        );

        UUID correlationId = UUID.randomUUID();
        IngestionLog logEntry = IngestionLog.builder()
                .sourceEndpoint(sourceEndpoint)
                .correlationId(correlationId)
                .build();

        IngestionStatus status = job.ingestSingleSource(sourceEndpoint, correlationId, null, logEntry);

        assertThat(status).isEqualTo(IngestionStatus.SKIPPED);

        ArgumentCaptor<IngestionLog> logCaptor = ArgumentCaptor.forClass(IngestionLog.class);
        verify(ingestionLogRepository, atLeastOnce()).save(logCaptor.capture());
        IngestionLog saved = logCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(IngestionStatus.SKIPPED);
        assertThat(saved.getErrorDetails()).contains("Robots.txt disallows scraping");

        verifyNoInteractions(fetcher);
        verifyNoInteractions(articleDiscoveryService, articleEnrichmentService, articleIndexingService);
    }

    @Test
    void ingestSkipsWhenEndpointBlockedUntilFuture() {
        sourceEndpoint.setBlockedUntil(Instant.now().plus(Duration.ofHours(2)));

        EndpointIngestionJob job = new EndpointIngestionJob(
                sourceEndpointRepository,
                ingestionLogRepository,
                List.of(fetcher),
                articleDiscoveryService,
                articleEnrichmentService,
                articleIndexingService,
                robotsService
        );

        UUID correlationId = UUID.randomUUID();
        IngestionLog logEntry = IngestionLog.builder()
                .sourceEndpoint(sourceEndpoint)
                .correlationId(correlationId)
                .build();

        IngestionStatus status = job.ingestSingleSource(sourceEndpoint, correlationId, null, logEntry);

        assertThat(status).isEqualTo(IngestionStatus.SKIPPED);

        verifyNoInteractions(fetcher);
        verifyNoInteractions(articleDiscoveryService, articleEnrichmentService, articleIndexingService);
    }

    @Test
    void ingestMarksRobotsDisallowedWhenSampleUrlFailsRobotsCheck() {
        when(fetcher.supports(sourceEndpoint)).thenReturn(true);
        when(fetcher.fetch(sourceEndpoint)).thenReturn(List.of(
                RawArticle.builder().externalUrl("https://example.com/a").build()
        ));
        when(robotsService.isAllowed("https://example.com/a")).thenReturn(false);

        EndpointIngestionJob job = new EndpointIngestionJob(
                sourceEndpointRepository,
                ingestionLogRepository,
                List.of(fetcher),
                articleDiscoveryService,
                articleEnrichmentService,
                articleIndexingService,
                robotsService
        );

        UUID correlationId = UUID.randomUUID();
        IngestionLog logEntry = IngestionLog.builder()
                .sourceEndpoint(sourceEndpoint)
                .correlationId(correlationId)
                .build();

        IngestionStatus status = job.ingestSingleSource(sourceEndpoint, correlationId, null, logEntry);

        assertThat(status).isEqualTo(IngestionStatus.SKIPPED);

        ArgumentCaptor<SourceEndpoint> epCaptor = ArgumentCaptor.forClass(SourceEndpoint.class);
        verify(sourceEndpointRepository, atLeastOnce()).save(epCaptor.capture());
        SourceEndpoint saved = epCaptor.getValue();
        assertThat(saved.isRobotsDisallowed()).isTrue();
        assertThat(saved.getBlockedUntil()).isNull();
        assertThat(saved.getBlockReason()).isEqualTo("ROBOTS_DISALLOWED");
        assertThat(saved.getBlockCount()).isEqualTo(0);
    }

    @Test
    void ingestAppliesBlockSignal_incrementsCount_butDoesNotBlockWhenThresholdNotReached() {
        when(fetcher.supports(sourceEndpoint)).thenReturn(true);

        RawArticle raw = RawArticle.builder().externalUrl("https://example.com/a").sourceItemId("a").build();
        when(fetcher.fetch(sourceEndpoint)).thenReturn(List.of(raw));
        when(robotsService.isAllowed("https://example.com/a")).thenReturn(true);

        when(articleDiscoveryService.shouldSkip(raw)).thenReturn(false);

        var article = Article.builder().id(10L).build();
        when(articleDiscoveryService.discover(sourceEndpoint, raw))
                .thenReturn(new ArticleDiscoveryService.DiscoveryResult(article, true));

        ArticleFetchResult fetchResult = ArticleFetchResult.builder().blockedSuspected(true).build();
        when(articleEnrichmentService.enrich(article, raw))
                .thenReturn(new ArticleEnrichmentService.EnrichmentResult(false, null, fetchResult));

        EndpointIngestionJob job = new EndpointIngestionJob(
                sourceEndpointRepository,
                ingestionLogRepository,
                List.of(fetcher),
                articleDiscoveryService,
                articleEnrichmentService,
                articleIndexingService,
                robotsService
        );

        ReflectionTestUtils.setField(job, "blockThreshold", 2);
        ReflectionTestUtils.setField(job, "blockDuration", Duration.ofHours(24));

        IngestionStatus status = job.ingestSingleSource(
                sourceEndpoint,
                UUID.randomUUID(),
                null,
                IngestionLog.builder().sourceEndpoint(sourceEndpoint).build()
        );

        assertThat(status).isEqualTo(IngestionStatus.FAILED);
        assertThat(sourceEndpoint.getBlockCount()).isEqualTo(1);
        assertThat(sourceEndpoint.getBlockedUntil()).isNull();
        assertThat(sourceEndpoint.getBlockReason()).isEqualTo("BLOCKED_OR_CAPTCHA");
    }

    @Test
    void ingestAppliesBlockSignalAndSetsBlockedUntilWhenThresholdReached() {
        when(fetcher.supports(sourceEndpoint)).thenReturn(true);
        RawArticle raw = RawArticle.builder()
                .externalUrl("https://example.com/a")
                .sourceItemId("a")
                .build();
        when(fetcher.fetch(sourceEndpoint)).thenReturn(List.of(raw));
        when(robotsService.isAllowed("https://example.com/a")).thenReturn(true);

        when(articleDiscoveryService.shouldSkip(raw)).thenReturn(false);

        var article = Article.builder().id(10L).build();
        when(articleDiscoveryService.discover(sourceEndpoint, raw))
                .thenReturn(new ArticleDiscoveryService.DiscoveryResult(article, true));

        ArticleFetchResult fetchResult = ArticleFetchResult.builder()
                .blockedSuspected(true)
                .build();
        when(articleEnrichmentService.enrich(article, raw))
                .thenReturn(new ArticleEnrichmentService.EnrichmentResult(false, null, fetchResult));

        EndpointIngestionJob job = new EndpointIngestionJob(
                sourceEndpointRepository,
                ingestionLogRepository,
                List.of(fetcher),
                articleDiscoveryService,
                articleEnrichmentService,
                articleIndexingService,
                robotsService
        );

        ReflectionTestUtils.setField(job, "blockThreshold", 1);
        ReflectionTestUtils.setField(job, "blockDuration", Duration.ofHours(24));

        UUID correlationId = UUID.randomUUID();
        IngestionLog logEntry = IngestionLog.builder()
                .sourceEndpoint(sourceEndpoint)
                .correlationId(correlationId)
                .build();

        IngestionStatus status = job.ingestSingleSource(sourceEndpoint, correlationId, null, logEntry);

        assertThat(status).isEqualTo(IngestionStatus.FAILED);
        assertThat(sourceEndpoint.getBlockedUntil()).isNotNull();
        assertThat(sourceEndpoint.getBlockedUntil()).isAfter(Instant.now());
        assertThat(sourceEndpoint.getBlockReason()).isEqualTo("BLOCKED_OR_CAPTCHA");
        assertThat(sourceEndpoint.getBlockCount()).isEqualTo(1);
    }

    @Test
    void ingestMarksRobotsDisallowed_whenEnrichmentFetchErrorMentionsRobotsTxt() {
        when(fetcher.supports(sourceEndpoint)).thenReturn(true);

        RawArticle raw = RawArticle.builder().externalUrl("https://example.com/a").sourceItemId("a").build();
        when(fetcher.fetch(sourceEndpoint)).thenReturn(List.of(raw));
        when(robotsService.isAllowed("https://example.com/a")).thenReturn(true);

        when(articleDiscoveryService.shouldSkip(raw)).thenReturn(false);

        var article = Article.builder().id(10L).build();
        when(articleDiscoveryService.discover(sourceEndpoint, raw))
                .thenReturn(new ArticleDiscoveryService.DiscoveryResult(article, true));

        ArticleFetchResult fetchResult = ArticleFetchResult.builder()
                .fetchError("Robots.txt disallows scraping for https://example.com/a")
                .build();
        when(articleEnrichmentService.enrich(article, raw))
                .thenReturn(new ArticleEnrichmentService.EnrichmentResult(false, null, fetchResult));

        EndpointIngestionJob job = new EndpointIngestionJob(
                sourceEndpointRepository,
                ingestionLogRepository,
                List.of(fetcher),
                articleDiscoveryService,
                articleEnrichmentService,
                articleIndexingService,
                robotsService
        );

        IngestionStatus status = job.ingestSingleSource(
                sourceEndpoint,
                UUID.randomUUID(),
                null,
                IngestionLog.builder().sourceEndpoint(sourceEndpoint).build()
        );

        assertThat(status).isEqualTo(IngestionStatus.FAILED);
        assertThat(sourceEndpoint.isRobotsDisallowed()).isTrue();
        assertThat(sourceEndpoint.getBlockReason()).isEqualTo("ROBOTS_DISALLOWED");
        assertThat(sourceEndpoint.getBlockedUntil()).isNull();
        assertThat(sourceEndpoint.getBlockCount()).isEqualTo(0);
    }

    @Test
    void ingestAppliesExtractionBlockOnlyWhenNoSuccess() {
        when(fetcher.supports(sourceEndpoint)).thenReturn(true);

        RawArticle raw = RawArticle.builder().externalUrl("https://example.com/a").sourceItemId("a").build();
        when(fetcher.fetch(sourceEndpoint)).thenReturn(List.of(raw));
        when(robotsService.isAllowed("https://example.com/a")).thenReturn(true);

        when(articleDiscoveryService.shouldSkip(raw)).thenReturn(false);

        var article = Article.builder().id(10L).build();
        when(articleDiscoveryService.discover(sourceEndpoint, raw))
                .thenReturn(new ArticleDiscoveryService.DiscoveryResult(article, true));

        ArticleFetchResult fetchResult = ArticleFetchResult.builder()
                .extractionError("Low-quality extraction (likely boilerplate or dynamic page)")
                .build();
        when(articleEnrichmentService.enrich(article, raw))
                .thenReturn(new ArticleEnrichmentService.EnrichmentResult(false, null, fetchResult));

        EndpointIngestionJob job = new EndpointIngestionJob(
                sourceEndpointRepository,
                ingestionLogRepository,
                List.of(fetcher),
                articleDiscoveryService,
                articleEnrichmentService,
                articleIndexingService,
                robotsService
        );

        ReflectionTestUtils.setField(job, "blockThreshold", 1);
        ReflectionTestUtils.setField(job, "blockDuration", Duration.ofHours(1));

        IngestionStatus status = job.ingestSingleSource(
                sourceEndpoint,
                UUID.randomUUID(),
                null,
                IngestionLog.builder().sourceEndpoint(sourceEndpoint).build()
        );

        assertThat(status).isEqualTo(IngestionStatus.FAILED);
        assertThat(sourceEndpoint.getBlockedUntil()).isNotNull();
        assertThat(sourceEndpoint.getBlockReason()).isEqualTo("EXTRACTION_FAILED");
    }

    @Test
    void ingestClearsBlockStateWhenHadSuccessAndNoBlockSignalsDetected() {
        sourceEndpoint.setBlockCount(2);
        sourceEndpoint.setBlockReason("BLOCKED_OR_CAPTCHA");
        sourceEndpoint.setBlockedUntil(Instant.now().minus(Duration.ofHours(1)));

        when(fetcher.supports(sourceEndpoint)).thenReturn(true);

        RawArticle ok = RawArticle.builder().externalUrl("https://example.com/ok").sourceItemId("ok").build();
        RawArticle other = RawArticle.builder().externalUrl("https://example.com/other").sourceItemId("o").build();
        when(fetcher.fetch(sourceEndpoint)).thenReturn(List.of(ok, other));
        when(robotsService.isAllowed("https://example.com/ok")).thenReturn(true);

        when(articleDiscoveryService.shouldSkip(ok)).thenReturn(false);
        when(articleDiscoveryService.shouldSkip(other)).thenReturn(false);

        var a1 = Article.builder().id(1L).build();
        var a2 = Article.builder().id(2L).build();

        when(articleDiscoveryService.discover(sourceEndpoint, ok))
                .thenReturn(new ArticleDiscoveryService.DiscoveryResult(a1, true));
        when(articleDiscoveryService.discover(sourceEndpoint, other))
                .thenReturn(new ArticleDiscoveryService.DiscoveryResult(a2, true));

        when(articleEnrichmentService.enrich(a1, ok))
                .thenReturn(new ArticleEnrichmentService.EnrichmentResult(true, "text", null));
        when(articleEnrichmentService.enrich(a2, other))
                .thenReturn(new ArticleEnrichmentService.EnrichmentResult(true, "text-2", null));

        when(articleIndexingService.index(eq(a1), eq("text"), anyString())).thenReturn(true);
        when(articleIndexingService.index(eq(a2), eq("text-2"), anyString())).thenReturn(false);

        EndpointIngestionJob job = new EndpointIngestionJob(
                sourceEndpointRepository,
                ingestionLogRepository,
                List.of(fetcher),
                articleDiscoveryService,
                articleEnrichmentService,
                articleIndexingService,
                robotsService
        );

        IngestionStatus status = job.ingestSingleSource(
                sourceEndpoint,
                UUID.randomUUID(),
                null,
                IngestionLog.builder().sourceEndpoint(sourceEndpoint).build()
        );

        assertThat(status).isEqualTo(IngestionStatus.PARTIAL);
        assertThat(sourceEndpoint.getBlockCount()).isEqualTo(0);
        assertThat(sourceEndpoint.getBlockReason()).isNull();
        assertThat(sourceEndpoint.getBlockedUntil()).isNull();
    }

    @Test
    void resetBatchCaches_callsResetOnlyForBatchResettableFetchers() {
        SourceFetcher resettable = mock(SourceFetcher.class, withSettings().extraInterfaces(BatchResettableFetcher.class));
        SourceFetcher notResettable = mock(SourceFetcher.class);

        EndpointIngestionJob job = new EndpointIngestionJob(
                sourceEndpointRepository,
                ingestionLogRepository,
                List.of(notResettable, resettable),
                articleDiscoveryService,
                articleEnrichmentService,
                articleIndexingService,
                robotsService
        );

        job.resetBatchCaches();

        verify((BatchResettableFetcher) resettable).resetBatch();
        verifyNoInteractions(notResettable);
    }


    @Test
    void ingestSingleSource_handlesUnexpectedExceptionInArticleLoop() {
        when(fetcher.supports(sourceEndpoint)).thenReturn(true);

        RawArticle raw = RawArticle.builder().externalUrl("https://example.com/a").sourceItemId("a").build();
        when(fetcher.fetch(sourceEndpoint)).thenReturn(List.of(raw));
        when(robotsService.isAllowed("https://example.com/a")).thenReturn(true);

        when(articleDiscoveryService.shouldSkip(raw)).thenReturn(false);

        when(articleDiscoveryService.discover(sourceEndpoint, raw))
                .thenThrow(new RuntimeException("boom"));

        EndpointIngestionJob job = new EndpointIngestionJob(
                sourceEndpointRepository,
                ingestionLogRepository,
                List.of(fetcher),
                articleDiscoveryService,
                articleEnrichmentService,
                articleIndexingService,
                robotsService
        );

        IngestionStatus status = job.ingestSingleSource(
                sourceEndpoint,
                UUID.randomUUID(),
                null,
                IngestionLog.builder().sourceEndpoint(sourceEndpoint).build()
        );

        assertThat(status).isEqualTo(IngestionStatus.FAILED);

        ArgumentCaptor<IngestionLog> logCaptor = ArgumentCaptor.forClass(IngestionLog.class);
        verify(ingestionLogRepository, atLeastOnce()).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getArticlesFailed()).isEqualTo(1);
    }

    @Test
    void ingestSingleSource_handlesProcessingFailuresAndSuccesses() {
        when(fetcher.supports(sourceEndpoint)).thenReturn(true);

        RawArticle ok = RawArticle.builder()
                .sourceItemId("ok-1")
                .externalUrl("https://example.com/good")
                .title("Good")
                .rawText("Some text")
                .build();
        RawArticle bad = RawArticle.builder()
                .sourceItemId("bad-1")
                .externalUrl("https://example.com/bad")
                .title("Bad")
                .rawText("More text")
                .build();

        when(fetcher.fetch(sourceEndpoint)).thenReturn(List.of(ok, bad));
        when(robotsService.isAllowed("https://example.com/good")).thenReturn(true);

        when(articleDiscoveryService.shouldSkip(ok)).thenReturn(false);
        when(articleDiscoveryService.shouldSkip(bad)).thenReturn(false);

        var articleOne = Article.builder().id(10L).build();
        var articleTwo = Article.builder().id(20L).build();

        when(articleDiscoveryService.discover(sourceEndpoint, ok))
                .thenReturn(new ArticleDiscoveryService.DiscoveryResult(articleOne, true));
        when(articleDiscoveryService.discover(sourceEndpoint, bad))
                .thenReturn(new ArticleDiscoveryService.DiscoveryResult(articleTwo, true));

        when(articleEnrichmentService.enrich(articleOne, ok))
                .thenReturn(new ArticleEnrichmentService.EnrichmentResult(true, "text-1", null));
        when(articleEnrichmentService.enrich(articleTwo, bad))
                .thenReturn(new ArticleEnrichmentService.EnrichmentResult(true, "text-2", null));

        when(articleIndexingService.index(articleOne, "text-1", "00000000-0000-0000-0000-000000000001"))
                .thenReturn(true);
        when(articleIndexingService.index(articleTwo, "text-2", "00000000-0000-0000-0000-000000000001"))
                .thenReturn(false);

        EndpointIngestionJob ingestionService = new EndpointIngestionJob(
                sourceEndpointRepository,
                ingestionLogRepository,
                List.of(fetcher),
                articleDiscoveryService,
                articleEnrichmentService,
                articleIndexingService,
                robotsService
        );

        UUID correlationId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        IngestionLog logEntry = IngestionLog.builder()
                .sourceEndpoint(sourceEndpoint)
                .correlationId(correlationId)
                .build();

        ingestionService.ingestSingleSource(sourceEndpoint, correlationId, null, logEntry);

        ArgumentCaptor<IngestionLog> captor = ArgumentCaptor.forClass(IngestionLog.class);
        verify(ingestionLogRepository, atLeastOnce()).save(captor.capture());
        IngestionLog finalLog = captor.getValue();

        assertThat(finalLog.getStatus()).isEqualTo(IngestionStatus.PARTIAL);
        assertThat(finalLog.getArticlesProcessed()).isEqualTo(1);
        assertThat(finalLog.getArticlesFailed()).isEqualTo(1);
        assertThat(finalLog.getCorrelationId()).isEqualTo(correlationId);

        verify(sourceEndpointRepository, atLeastOnce()).save(sourceEndpoint);
    }
}