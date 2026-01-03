package com.factcheck.collector.service;

import com.factcheck.collector.domain.entity.Article;
import com.factcheck.collector.domain.entity.ArticleSource;
import com.factcheck.collector.domain.entity.Publisher;
import com.factcheck.collector.domain.entity.SourceEndpoint;
import com.factcheck.collector.dto.ArticleContentResponse;
import com.factcheck.collector.repository.ArticleRepository;
import com.factcheck.collector.repository.ArticleSourceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleContentServiceTest {

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private ArticleSourceRepository articleSourceRepository;

    @Mock
    private WeaviateIndexingService weaviateIndexingService;

    @InjectMocks
    private ArticleContentService articleContentService;

    @Test
    void getArticleContentBuildsResponseFromChunks() {
        Publisher publisher = Publisher.builder()
                .id(5L)
                .name("Demo Publisher")
                .build();

        SourceEndpoint endpoint = SourceEndpoint.builder()
                .id(12L)
                .publisher(publisher)
                .displayName("Demo RSS")
                .build();

        Article article = Article.builder()
                .id(10L)
                .publisher(publisher)
                .canonicalUrl("https://example.com/article")
                .canonicalUrlHash("hash")
                .title("An Article")
                .publishedDate(Instant.parse("2024-01-02T03:04:05Z"))
                .build();

        when(articleRepository.findById(10L)).thenReturn(Optional.of(article));
        when(articleSourceRepository.findTopByArticleOrderByFetchedAtDesc(article))
                .thenReturn(Optional.of(ArticleSource.builder()
                        .article(article)
                        .sourceEndpoint(endpoint)
                        .sourceItemId("item-1")
                        .build()));
        when(weaviateIndexingService.getChunksForArticle(10L)).thenReturn(List.of("First", "Second"));

        ArticleContentResponse response = articleContentService.getArticleContent(10L);

        assertThat(response.getArticleId()).isEqualTo(10L);
        assertThat(response.getPublisherId()).isEqualTo(5L);
        assertThat(response.getPublisherName()).isEqualTo("Demo Publisher");
        assertThat(response.getSourceEndpointId()).isEqualTo(12L);
        assertThat(response.getSourceEndpointName()).isEqualTo("Demo RSS");
        assertThat(response.getCanonicalUrl()).isEqualTo("https://example.com/article");
        assertThat(response.getTitle()).isEqualTo("An Article");
        assertThat(response.getPublishedDate()).isEqualTo(Instant.parse("2024-01-02T03:04:05Z"));
        assertThat(response.getContent()).isEqualTo("First\n\nSecond");
    }

    @Test
    void getArticleContentThrowsWhenArticleMissing() {
        when(articleRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> articleContentService.getArticleContent(42L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Article not found");
    }

    @Test
    void getArticleContentThrowsWhenChunksMissing() {
        Publisher publisher = Publisher.builder().id(3L).name("No Chunks Publisher").build();
        Article article = Article.builder()
                .id(7L)
                .publisher(publisher)
                .title("Title")
                .canonicalUrl("url")
                .canonicalUrlHash("hash")
                .build();

        when(articleRepository.findById(7L)).thenReturn(Optional.of(article));
        when(weaviateIndexingService.getChunksForArticle(7L)).thenReturn(List.of());

        assertThatThrownBy(() -> articleContentService.getArticleContent(7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No content chunks");
    }
}