package com.factcheck.collector.service.ingestion.pipeline;

import com.factcheck.collector.domain.entity.Article;
import com.factcheck.collector.domain.enums.ArticleStatus;
import com.factcheck.collector.repository.ArticleRepository;
import com.factcheck.collector.service.processing.ArticleProcessingService;
import com.factcheck.collector.service.processing.EmbeddingService;
import com.factcheck.collector.service.processing.WeaviateIndexingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArticleIndexingServiceTest {

    @Mock private ArticleProcessingService articleProcessingService;
    @Mock private EmbeddingService embeddingService;
    @Mock private WeaviateIndexingService weaviateIndexingService;
    @Mock private ArticleRepository articleRepository;

    private ArticleIndexingService service;

    @BeforeEach
    void setUp() {
        service = new ArticleIndexingService(
                articleProcessingService,
                embeddingService,
                weaviateIndexingService,
                articleRepository
        );
    }

    @Test
    void indexHappyPathUsesResolvedArticleAndSetsIndexedFields() {
        Article input = Article.builder().id(10L).build();
        Article resolved = Article.builder().id(10L).build();

        when(articleRepository.findByIdWithPublisherAndMbfc(10L)).thenReturn(Optional.of(resolved));

        List<String> chunks = List.of("c1", "c2");
        List<List<Double>> embeddings = List.of(
                List.of(0.1d, 0.2d),
                List.of(0.3d, 0.4d)
        );

        when(articleProcessingService.createChunks(resolved, "text", "corr")).thenReturn(chunks);
        when(embeddingService.embedChunks(chunks, "corr")).thenReturn(embeddings);

        boolean ok = service.index(input, "text", "corr");

        assertThat(ok).isTrue();

        verify(weaviateIndexingService).indexArticleChunks(resolved, chunks, embeddings, "corr");

        ArgumentCaptor<Article> savedCaptor = ArgumentCaptor.forClass(Article.class);
        verify(articleRepository).save(savedCaptor.capture());
        Article saved = savedCaptor.getValue();

        assertThat(saved).isSameAs(resolved);
        assertThat(saved.getChunkCount()).isEqualTo(2);
        assertThat(saved.isWeaviateIndexed()).isTrue();
        assertThat(saved.getStatus()).isEqualTo(ArticleStatus.INDEXED);
    }

    @Test
    void indexUsesInputArticleWhenNoResolvedEntityReturned() {
        Article input = Article.builder().id(10L).build();

        when(articleRepository.findByIdWithPublisherAndMbfc(10L)).thenReturn(Optional.empty());

        List<String> chunks = List.of("c1");
        List<List<Double>> embeddings = List.of(List.of(0.1d));

        when(articleProcessingService.createChunks(input, "text", "corr")).thenReturn(chunks);
        when(embeddingService.embedChunks(chunks, "corr")).thenReturn(embeddings);

        boolean ok = service.index(input, "text", "corr");

        assertThat(ok).isTrue();

        verify(weaviateIndexingService).indexArticleChunks(input, chunks, embeddings, "corr");
        verify(articleRepository).save(input);

        assertThat(input.getChunkCount()).isEqualTo(1);
        assertThat(input.isWeaviateIndexed()).isTrue();
        assertThat(input.getStatus()).isEqualTo(ArticleStatus.INDEXED);
    }

    @Test
    void indexDoesNotResolveWhenArticleIdNull() {
        Article input = Article.builder().build();

        List<String> chunks = List.of("c1");
        List<List<Double>> embeddings = List.of(List.of(0.1d));

        when(articleProcessingService.createChunks(input, "text", "corr")).thenReturn(chunks);
        when(embeddingService.embedChunks(chunks, "corr")).thenReturn(embeddings);

        boolean ok = service.index(input, "text", "corr");

        assertThat(ok).isTrue();

        verify(articleRepository, never()).findByIdWithPublisherAndMbfc(any());
        verify(weaviateIndexingService).indexArticleChunks(input, chunks, embeddings, "corr");
        verify(articleRepository).save(input);
    }

    @Test
    void indexReturnsFalseAndMarksArticleErrorWhenAnyStepThrows() {
        Article input = Article.builder().id(10L).build();

        when(articleRepository.findByIdWithPublisherAndMbfc(10L)).thenReturn(Optional.of(input));
        when(articleProcessingService.createChunks(any(), any(), any()))
                .thenThrow(new RuntimeException("boom"));

        boolean ok = service.index(input, "text", "corr");

        assertThat(ok).isFalse();

        assertThat(input.isWeaviateIndexed()).isFalse();
        assertThat(input.getStatus()).isEqualTo(ArticleStatus.ERROR);
        assertThat(input.getExtractionError()).contains("Indexing failed: boom");

        verify(articleRepository).save(input);
        verifyNoInteractions(embeddingService);
        verifyNoInteractions(weaviateIndexingService);
    }

    @Test
    void indexReturnsFalseAndDoesNotSaveWhenArticleIsNull() {
        when(articleProcessingService.createChunks(any(), any(), any()))
                .thenThrow(new RuntimeException("boom"));

        boolean ok = service.index(null, "text", "corr");

        assertThat(ok).isFalse();
        verify(articleRepository, never()).save(any());
        verifyNoInteractions(embeddingService, weaviateIndexingService);
    }
}