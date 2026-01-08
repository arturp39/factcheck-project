package com.factcheck.collector.service.ingestion;

import com.factcheck.collector.domain.entity.IngestionLog;
import com.factcheck.collector.domain.entity.Publisher;
import com.factcheck.collector.domain.entity.SourceEndpoint;
import com.factcheck.collector.domain.enums.IngestionStatus;
import com.factcheck.collector.domain.enums.SourceKind;
import com.factcheck.collector.exception.FetchException;
import com.factcheck.collector.integration.ingestion.fetcher.RawArticle;
import com.factcheck.collector.integration.ingestion.fetcher.SourceFetcher;
import com.factcheck.collector.integration.ingestion.RobotsService;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
                .build();
    }

    @Test
    void ingestSingleSourceRecordsFetchFailure() {
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

        IngestionLog finalLog = logCaptor.getAllValues().getLast();
        assertThat(finalLog.getStatus()).isEqualTo(IngestionStatus.FAILED);
        assertThat(finalLog.getErrorDetails()).contains("Fetch error: boom");
        assertThat(finalLog.getCorrelationId()).isEqualTo(correlationId);

        verifyNoInteractions(articleDiscoveryService, articleEnrichmentService, articleIndexingService);

        ArgumentCaptor<SourceEndpoint> endpointCaptor = ArgumentCaptor.forClass(SourceEndpoint.class);
        verify(sourceEndpointRepository, atLeastOnce()).save(endpointCaptor.capture());
        SourceEndpoint saved = endpointCaptor.getAllValues().getLast();
        assertThat(saved.getFailureCount()).isEqualTo(1);
    }

    @Test
    void ingestSingleSourceHandlesProcessingFailuresAndSuccesses() {
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

        var articleOne = com.factcheck.collector.domain.entity.Article.builder().id(10L).build();
        var articleTwo = com.factcheck.collector.domain.entity.Article.builder().id(20L).build();

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
        IngestionLog finalLog = captor.getAllValues().getLast();
        assertThat(finalLog.getStatus()).isEqualTo(IngestionStatus.PARTIAL);
        assertThat(finalLog.getArticlesProcessed()).isEqualTo(1);
        assertThat(finalLog.getArticlesFailed()).isEqualTo(1);
        assertThat(finalLog.getCorrelationId()).isEqualTo(correlationId);

        verify(sourceEndpointRepository, atLeastOnce()).save(sourceEndpoint);
    }
}