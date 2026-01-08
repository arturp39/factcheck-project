package com.factcheck.collector.service.ingestion.pipeline;

import com.factcheck.collector.domain.entity.Article;
import com.factcheck.collector.domain.entity.Publisher;
import com.factcheck.collector.domain.entity.SourceEndpoint;
import com.factcheck.collector.domain.enums.ArticleStatus;
import com.factcheck.collector.integration.ingestion.fetcher.RawArticle;
import com.factcheck.collector.repository.ArticleRepository;
import com.factcheck.collector.repository.ArticleSourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleDiscoveryServiceTest {

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private ArticleSourceRepository articleSourceRepository;

    private ArticleDiscoveryService service;

    @BeforeEach
    void setUp() {
        service = new ArticleDiscoveryService(articleRepository, articleSourceRepository);
    }

    @Test
    void discoverSkipsWhenUrlMissing() {
        RawArticle raw = RawArticle.builder().externalUrl(" ").build();
        SourceEndpoint endpoint = SourceEndpoint.builder()
                .publisher(Publisher.builder().id(1L).name("Pub").build())
                .build();

        var result = service.discover(endpoint, raw);

        assertThat(result).isNull();
        verify(articleRepository, never()).save(any());
    }

    @Test
    void discoverSkipsWhenSourceItemAlreadyExists() {
        SourceEndpoint endpoint = SourceEndpoint.builder()
                .publisher(Publisher.builder().id(1L).name("Pub").build())
                .build();
        RawArticle raw = RawArticle.builder()
                .externalUrl("https://example.com/a")
                .sourceItemId("item-1")
                .build();

        when(articleSourceRepository.existsBySourceEndpointAndSourceItemId(endpoint, "item-1"))
                .thenReturn(true);

        var result = service.discover(endpoint, raw);

        assertThat(result).isNull();
        verify(articleRepository, never()).save(any());
    }

    @Test
    void discoverCreatesArticleAndLinkForNewItem() {
        Publisher publisher = Publisher.builder().id(1L).name("Pub").build();
        SourceEndpoint endpoint = SourceEndpoint.builder()
                .id(2L)
                .publisher(publisher)
                .build();
        RawArticle raw = RawArticle.builder()
                .externalUrl("https://example.com/a")
                .title("Title")
                .description("Desc")
                .publishedDate(Instant.parse("2024-01-01T00:00:00Z"))
                .build();

        when(articleSourceRepository.existsBySourceEndpointAndSourceItemId(endpoint, "https://example.com/a"))
                .thenReturn(false);
        when(articleRepository.findByPublisherAndCanonicalUrlHash(any(), any()))
                .thenReturn(Optional.empty());
        when(articleRepository.save(any(Article.class)))
                .thenAnswer(invocation -> {
                    Article saved = invocation.getArgument(0);
                    saved.setId(10L);
                    return saved;
                });

        var result = service.discover(endpoint, raw);

        assertThat(result).isNotNull();
        assertThat(result.isNew()).isTrue();
        assertThat(result.article().getStatus()).isEqualTo(ArticleStatus.DISCOVERED);

        ArgumentCaptor<Article> articleCaptor = ArgumentCaptor.forClass(Article.class);
        verify(articleRepository).save(articleCaptor.capture());
        assertThat(articleCaptor.getValue().getCanonicalUrl()).isEqualTo("https://example.com/a");
        assertThat(articleCaptor.getValue().getTitle()).isEqualTo("Title");
    }

    @Test
    void discoverUpdatesExistingArticle() {
        Publisher publisher = Publisher.builder().id(1L).name("Pub").build();
        SourceEndpoint endpoint = SourceEndpoint.builder()
                .id(2L)
                .publisher(publisher)
                .build();
        RawArticle raw = RawArticle.builder()
                .externalUrl("https://example.com/a")
                .sourceItemId("item-1")
                .build();

        Article existing = Article.builder()
                .id(5L)
                .publisher(publisher)
                .canonicalUrl("https://example.com/a")
                .canonicalUrlHash("hash")
                .title("Old")
                .status(ArticleStatus.DISCOVERED)
                .build();

        when(articleSourceRepository.existsBySourceEndpointAndSourceItemId(endpoint, "item-1"))
                .thenReturn(false);
        when(articleRepository.findByPublisherAndCanonicalUrlHash(any(), any()))
                .thenReturn(Optional.of(existing));
        when(articleRepository.save(any(Article.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.discover(endpoint, raw);

        assertThat(result).isNotNull();
        assertThat(result.isNew()).isFalse();
        verify(articleRepository).save(existing);
    }

    @Test
    void shouldSkipDetectsNonTextUrls() {
        RawArticle rawWithText = RawArticle.builder()
                .rawText("text")
                .externalUrl("https://example.com/video/1")
                .build();
        RawArticle rawVideo = RawArticle.builder()
                .externalUrl("https://example.com/video/1")
                .build();
        RawArticle rawOk = RawArticle.builder()
                .externalUrl("https://example.com/news/1")
                .build();

        assertThat(service.shouldSkip(rawWithText)).isFalse();
        assertThat(service.shouldSkip(rawVideo)).isTrue();
        assertThat(service.shouldSkip(rawOk)).isFalse();
    }

    @Test
    void discoverReturnsNullWhenDuplicateArticleDetectedAtDbLevel() {
        SourceEndpoint endpoint = SourceEndpoint.builder()
                .publisher(Publisher.builder().id(1L).name("Pub").build())
                .build();
        RawArticle raw = RawArticle.builder()
                .externalUrl("https://example.com/a")
                .title("Title")
                .build();

        when(articleSourceRepository.existsBySourceEndpointAndSourceItemId(endpoint, "https://example.com/a"))
                .thenReturn(false);
        when(articleRepository.findByPublisherAndCanonicalUrlHash(any(), any()))
                .thenReturn(Optional.empty());
        when(articleRepository.save(any(Article.class)))
                .thenThrow(new DataIntegrityViolationException("dup"));

        var result = service.discover(endpoint, raw);

        assertThat(result).isNull();
        verify(articleSourceRepository, never()).save(any());
    }

    @Test
    void discoverStillReturnsResultWhenArticleSourceLinkDuplicate() {
        Publisher publisher = Publisher.builder().id(1L).name("Pub").build();
        SourceEndpoint endpoint = SourceEndpoint.builder()
                .id(2L)
                .publisher(publisher)
                .build();
        RawArticle raw = RawArticle.builder()
                .externalUrl("https://example.com/a")
                .title("Title")
                .build();

        when(articleSourceRepository.existsBySourceEndpointAndSourceItemId(endpoint, "https://example.com/a"))
                .thenReturn(false);
        when(articleRepository.findByPublisherAndCanonicalUrlHash(any(), any()))
                .thenReturn(Optional.empty());
        when(articleRepository.save(any(Article.class)))
                .thenAnswer(invocation -> {
                    Article saved = invocation.getArgument(0);
                    saved.setId(10L);
                    return saved;
                });
        when(articleSourceRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("dup-link"));

        var result = service.discover(endpoint, raw);

        assertThat(result).isNotNull();
        assertThat(result.isNew()).isTrue();
        assertThat(result.article().getStatus()).isEqualTo(ArticleStatus.DISCOVERED);
    }
}
