package com.factcheck.collector.service;

import com.factcheck.collector.domain.entity.IngestionLog;
import com.factcheck.collector.domain.entity.Publisher;
import com.factcheck.collector.domain.entity.SourceEndpoint;
import com.factcheck.collector.domain.enums.IngestionStatus;
import com.factcheck.collector.domain.enums.SourceKind;
import com.factcheck.collector.exception.FetchException;
import com.factcheck.collector.exception.ProcessingFailedException;
import com.factcheck.collector.integration.fetcher.RawArticle;
import com.factcheck.collector.integration.fetcher.SourceFetcher;
import com.factcheck.collector.repository.ArticleRepository;
import com.factcheck.collector.repository.ArticleSourceRepository;
import com.factcheck.collector.repository.IngestionLogRepository;
import com.factcheck.collector.repository.SourceEndpointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SourceIngestionServiceTest {

    @Mock
    private SourceEndpointRepository sourceEndpointRepository;

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private ArticleSourceRepository articleSourceRepository;

    @Mock
    private IngestionLogRepository ingestionLogRepository;

    @Mock
    private ArticleProcessingService articleProcessingService;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private WeaviateIndexingService weaviateIndexingService;

    @Mock
    private SourceFetcher fetcher;

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
    void ingestSingleSourceRecordsFetchFailure() throws FetchException {
        when(fetcher.supports(SourceKind.RSS)).thenReturn(true);
        when(fetcher.fetch(sourceEndpoint)).thenThrow(new FetchException("boom"));

        SourceIngestionService ingestionService = new SourceIngestionService(
                articleSourceRepository,
                sourceEndpointRepository,
                articleRepository,
                ingestionLogRepository,
                List.of(fetcher),
                articleProcessingService,
                embeddingService,
                weaviateIndexingService
        );

        ingestionService.ingestSingleSource(sourceEndpoint, "corr-fail");

        ArgumentCaptor<IngestionLog> logCaptor = ArgumentCaptor.forClass(IngestionLog.class);
        verify(ingestionLogRepository, atLeastOnce()).save(logCaptor.capture());

        IngestionLog finalLog = logCaptor.getAllValues().getLast();
        assertThat(finalLog.getStatus()).isEqualTo(IngestionStatus.FAILED);
        assertThat(finalLog.getErrorDetails()).contains("Fetch error: boom");
        assertThat(finalLog.getCorrelationId()).isEqualTo("corr-fail");

        verifyNoInteractions(articleRepository, articleProcessingService, embeddingService, weaviateIndexingService);
    }

    @Test
    void ingestSingleSourceHandlesProcessingFailuresAndSuccesses() throws Exception {
        when(fetcher.supports(SourceKind.RSS)).thenReturn(true);
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

        when(articleSourceRepository.existsBySourceEndpointAndSourceItemId(sourceEndpoint, "ok-1")).thenReturn(false);
        when(articleSourceRepository.existsBySourceEndpointAndSourceItemId(sourceEndpoint, "bad-1")).thenReturn(false);
        when(articleRepository.findByPublisherAndCanonicalUrlHash(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());

        // first article succeeds
        when(articleRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> {
                    var a = (com.factcheck.collector.domain.entity.Article) inv.getArgument(0);
                    a.setId(10L);
                    return a;
                });
        when(articleProcessingService.createChunks(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(List.of("chunk1"));
        when(embeddingService.embedChunks(List.of("chunk1"), "corr")).thenReturn(List.of(List.of(0.1)));

        // first index succeeds, second fails
        java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger();
        org.mockito.Mockito.doAnswer(inv -> {
                    if (count.getAndIncrement() == 0) {
                        return null;
                    }
                    throw new ProcessingFailedException(new RuntimeException("fail"));
                })
                .when(weaviateIndexingService)
                .indexArticleChunks(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq("corr"));

        SourceIngestionService ingestionService = new SourceIngestionService(
                articleSourceRepository,
                sourceEndpointRepository,
                articleRepository,
                ingestionLogRepository,
                List.of(fetcher),
                articleProcessingService,
                embeddingService,
                weaviateIndexingService
        );

        ingestionService.ingestSingleSource(sourceEndpoint, "corr");

        ArgumentCaptor<IngestionLog> captor = ArgumentCaptor.forClass(IngestionLog.class);
        verify(ingestionLogRepository, atLeastOnce()).save(captor.capture());
        IngestionLog finalLog = captor.getAllValues().getLast();
        assertThat(finalLog.getStatus()).isEqualTo(IngestionStatus.PARTIAL);
        assertThat(finalLog.getArticlesProcessed()).isEqualTo(1);
        assertThat(finalLog.getArticlesFailed()).isEqualTo(1);
        assertThat(finalLog.getCorrelationId()).isEqualTo("corr");
    }

    @Test
    void ingestSingleSourceSkipsNonTextMediaPages() throws Exception {
        when(fetcher.supports(SourceKind.RSS)).thenReturn(true);
        RawArticle video = RawArticle.builder()
                .sourceItemId("vid-1")
                .externalUrl("https://example.com/video/abc")
                .title("Video")
                .rawText("")
                .build();
        when(fetcher.fetch(sourceEndpoint)).thenReturn(List.of(video));

        SourceIngestionService ingestionService = new SourceIngestionService(
                articleSourceRepository,
                sourceEndpointRepository,
                articleRepository,
                ingestionLogRepository,
                List.of(fetcher),
                articleProcessingService,
                embeddingService,
                weaviateIndexingService
        );

        ingestionService.ingestSingleSource(sourceEndpoint, "corr-skip");

        verify(articleRepository, never()).save(org.mockito.ArgumentMatchers.any());
        ArgumentCaptor<IngestionLog> captor = ArgumentCaptor.forClass(IngestionLog.class);
        verify(ingestionLogRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().getLast().getArticlesProcessed()).isEqualTo(0);
    }
}
