package com.factcheck.collector.service.read;

import com.factcheck.collector.dto.ChunkResult;
import com.factcheck.collector.dto.SearchRequest;
import com.factcheck.collector.dto.SearchResponse;
import com.factcheck.collector.service.processing.WeaviateIndexingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleSearchServiceTest {

    @Mock
    private WeaviateIndexingService weaviateIndexingService;

    private ArticleSearchService articleSearchService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        articleSearchService = new ArticleSearchService(weaviateIndexingService);
        ReflectionTestUtils.setField(articleSearchService, "embeddingDimension", 3);
    }

    @Test
    void searchReturnsResponseWithMetadata() {
        List<Double> embedding = List.of(0.1d, 0.2d, 0.3d);
        SearchRequest request = new SearchRequest(embedding, 2, 0.5f, null);

        List<ChunkResult> results = List.of(
                ChunkResult.builder()
                        .text("Snippet")
                        .articleId(1L)
                        .articleUrl("https://example.com")
                        .articleTitle("Title")
                        .sourceName("Source")
                        .publishedDate(LocalDateTime.parse("2024-02-01T12:30:00"))
                        .chunkIndex(0)
                        .score(0.9f)
                        .build()
        );

        when(weaviateIndexingService.searchByEmbedding(embedding, 2, 0.5f, "corr"))
                .thenReturn(results);

        SearchResponse response = articleSearchService.search(request, "corr");

        verify(weaviateIndexingService).searchByEmbedding(embedding, 2, 0.5f, "corr");
        assertThat(response.results()).containsExactlyElementsOf(results);
        assertThat(response.totalFound()).isEqualTo(1);
        assertThat(response.correlationId()).isEqualTo("corr");
        assertThat(response.executionTimeMs()).isNotNull();
    }

    @Test
    void searchFailsOnWrongEmbeddingDimension() {
        List<Double> embedding = List.of(0.1d, 0.2d);
        SearchRequest request = new SearchRequest(embedding, 2, 0.5f, null);

        assertThatThrownBy(() -> articleSearchService.search(request, "corr"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Embedding must have dimension 3");
    }
}