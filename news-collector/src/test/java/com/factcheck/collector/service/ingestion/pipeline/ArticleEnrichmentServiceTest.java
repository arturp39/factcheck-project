package com.factcheck.collector.service.ingestion.pipeline;

import com.factcheck.collector.domain.entity.Article;
import com.factcheck.collector.domain.entity.ArticleContent;
import com.factcheck.collector.domain.enums.ArticleStatus;
import com.factcheck.collector.integration.ingestion.fetcher.ArticleFetchResult;
import com.factcheck.collector.integration.ingestion.fetcher.RawArticle;
import com.factcheck.collector.integration.ingestion.fetcher.ArticleContentExtractor;
import com.factcheck.collector.repository.ArticleContentRepository;
import com.factcheck.collector.repository.ArticleRepository;
import com.factcheck.collector.util.HashUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArticleEnrichmentServiceTest {

    @Mock private ArticleRepository articleRepository;
    @Mock private ArticleContentRepository articleContentRepository;
    @Mock private ArticleContentExtractor contentExtractor;

    private ArticleEnrichmentService service;

    @BeforeEach
    void setUp() {
        service = new ArticleEnrichmentService(articleRepository, articleContentRepository, contentExtractor);
    }

    @Test
    void enrichRejectsNullArticleOrId() {
        RawArticle raw = RawArticle.builder().rawText("x").build();

        assertThatThrownBy(() -> service.enrich(null, raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Article id is required for enrichment");

        Article noId = Article.builder().canonicalUrl("https://example.com/a").build();
        assertThatThrownBy(() -> service.enrich(noId, raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Article id is required for enrichment");

        verifyNoInteractions(articleRepository, articleContentRepository, contentExtractor);
    }

    @Test
    void enrichUsesProvidedTextDoesNotCallExtractorAndSucceeds() {
        Article input = Article.builder().id(10L).canonicalUrl("https://example.com/a").build();
        Article managed = Article.builder().id(10L).canonicalUrl("https://example.com/a").build();
        RawArticle raw = RawArticle.builder().rawText("Provided text").build();

        when(articleRepository.getReferenceById(10L)).thenReturn(managed);
        when(articleContentRepository.findById(10L)).thenReturn(Optional.empty());

        var result = service.enrich(input, raw);

        assertThat(result.success()).isTrue();
        assertThat(result.extractedText()).isEqualTo("Provided text");
        assertThat(result.fetchResult()).isNotNull();

        verifyNoInteractions(contentExtractor);

        assertThat(managed.getStatus()).isEqualTo(ArticleStatus.EXTRACTED);
        assertThat(managed.getContentHash()).isEqualTo(HashUtils.sha256Hex("Provided text"));

        ArgumentCaptor<ArticleContent> contentCaptor = ArgumentCaptor.forClass(ArticleContent.class);
        verify(articleContentRepository).save(contentCaptor.capture());
        assertThat(contentCaptor.getValue().getExtractedText()).isEqualTo("Provided text");
        assertThat(contentCaptor.getValue().getExtractedAt()).isNotNull();
    }

    @Test
    void enrichUsesExtractorWhenNoProvidedText() {
        Article input = Article.builder().id(10L).canonicalUrl("https://example.com/a").build();
        Article managed = Article.builder().id(10L).canonicalUrl("https://example.com/a").build();
        RawArticle raw = RawArticle.builder().rawText("  ").build();

        ArticleFetchResult fetchResult = ArticleFetchResult.builder()
                .httpStatus(200)
                .extractedText("Extracted")
                .build();

        when(contentExtractor.fetchAndExtract("https://example.com/a")).thenReturn(fetchResult);
        when(articleRepository.getReferenceById(10L)).thenReturn(managed);
        when(articleContentRepository.findById(10L)).thenReturn(Optional.empty());

        var result = service.enrich(input, raw);

        assertThat(result.success()).isTrue();
        assertThat(result.extractedText()).isEqualTo("Extracted");
        verify(contentExtractor).fetchAndExtract("https://example.com/a");
    }

    @Test
    void enrichReturnsFailureWhenFetchResultNull() {
        Article input = Article.builder().id(10L).canonicalUrl("https://example.com/a").build();
        Article managed = Article.builder().id(10L).canonicalUrl("https://example.com/a").build();
        RawArticle raw = RawArticle.builder().rawText(" ").build();

        when(contentExtractor.fetchAndExtract("https://example.com/a")).thenReturn(null);
        when(articleRepository.getReferenceById(10L)).thenReturn(managed);

        var result = service.enrich(input, raw);

        assertThat(result.success()).isFalse();
        assertThat(result.extractedText()).isNull();
        assertThat(result.fetchResult()).isNull();

        assertThat(managed.getStatus()).isEqualTo(ArticleStatus.ERROR);
        assertThat(managed.getFetchError()).isEqualTo("Fetch failed");

        verify(articleContentRepository, never()).save(any());
    }

    @Test
    void enrichReturnsFailureWhenHttpStatusNon2xx() {
        Article input = Article.builder().id(10L).canonicalUrl("https://example.com/a").build();
        Article managed = Article.builder().id(10L).canonicalUrl("https://example.com/a").build();
        RawArticle raw = RawArticle.builder().rawText(" ").build();

        ArticleFetchResult fetchResult = ArticleFetchResult.builder()
                .httpStatus(503)
                .extractedText("ignored")
                .build();

        when(contentExtractor.fetchAndExtract("https://example.com/a")).thenReturn(fetchResult);
        when(articleRepository.getReferenceById(10L)).thenReturn(managed);

        var result = service.enrich(input, raw);

        assertThat(result.success()).isFalse();
        assertThat(managed.getStatus()).isEqualTo(ArticleStatus.ERROR);
        assertThat(managed.getFetchError()).isEqualTo("HTTP status 503");

        verify(articleContentRepository, never()).save(any());
    }

    @Test
    void enrichReturnsFailureWhenFetchErrorPresent() {
        Article input = Article.builder().id(10L).canonicalUrl("https://example.com/a").build();
        Article managed = Article.builder().id(10L).canonicalUrl("https://example.com/a").build();
        RawArticle raw = RawArticle.builder().rawText(" ").build();

        ArticleFetchResult fetchResult = ArticleFetchResult.builder()
                .httpStatus(200)
                .fetchError("timeout")
                .extractedText("ignored")
                .build();

        when(contentExtractor.fetchAndExtract("https://example.com/a")).thenReturn(fetchResult);
        when(articleRepository.getReferenceById(10L)).thenReturn(managed);

        var result = service.enrich(input, raw);

        assertThat(result.success()).isFalse();
        assertThat(managed.getStatus()).isEqualTo(ArticleStatus.ERROR);
        assertThat(managed.getFetchError()).isEqualTo("timeout");

        verify(articleContentRepository, never()).save(any());
    }

    @Test
    void enrichReturnsFailureWhenExtractionErrorPresent() {
        Article input = Article.builder().id(10L).canonicalUrl("https://example.com/a").build();
        Article managed = Article.builder().id(10L).canonicalUrl("https://example.com/a").build();
        RawArticle raw = RawArticle.builder().rawText(" ").build();

        ArticleFetchResult fetchResult = ArticleFetchResult.builder()
                .httpStatus(200)
                .extractionError("blocked by paywall")
                .extractedText(null)
                .build();

        when(contentExtractor.fetchAndExtract("https://example.com/a")).thenReturn(fetchResult);
        when(articleRepository.getReferenceById(10L)).thenReturn(managed);

        var result = service.enrich(input, raw);

        assertThat(result.success()).isFalse();
        assertThat(managed.getStatus()).isEqualTo(ArticleStatus.ERROR);
        assertThat(managed.getExtractionError()).isEqualTo("blocked by paywall");

        verify(articleContentRepository, never()).save(any());
    }

    @Test
    void enrichReturnsFailureWhenExtractedTextBlankAndNoExtractionError() {
        Article input = Article.builder().id(10L).canonicalUrl("https://example.com/a").build();
        Article managed = Article.builder().id(10L).canonicalUrl("https://example.com/a").build();
        RawArticle raw = RawArticle.builder().rawText(" ").build();

        ArticleFetchResult fetchResult = ArticleFetchResult.builder()
                .httpStatus(200)
                .extractedText("   ")
                .build();

        when(contentExtractor.fetchAndExtract("https://example.com/a")).thenReturn(fetchResult);
        when(articleRepository.getReferenceById(10L)).thenReturn(managed);

        var result = service.enrich(input, raw);

        assertThat(result.success()).isFalse();
        assertThat(managed.getStatus()).isEqualTo(ArticleStatus.ERROR);
        assertThat(managed.getExtractionError()).isEqualTo("No meaningful text extracted");

        verify(articleContentRepository, never()).save(any());
    }

    @Test
    void enrichUpsertsContentWhenExistingRowPresent() {
        Article input = Article.builder().id(10L).canonicalUrl("https://example.com/a").build();
        Article managed = Article.builder().id(10L).canonicalUrl("https://example.com/a").build();
        RawArticle raw = RawArticle.builder().rawText("Provided text").build();

        when(articleRepository.getReferenceById(10L)).thenReturn(managed);

        ArticleContent existing = ArticleContent.builder().article(managed).build();
        when(articleContentRepository.findById(10L)).thenReturn(Optional.of(existing));

        service.enrich(input, raw);

        ArgumentCaptor<ArticleContent> contentCaptor = ArgumentCaptor.forClass(ArticleContent.class);
        verify(articleContentRepository).save(contentCaptor.capture());
        assertThat(contentCaptor.getValue()).isSameAs(existing);
        assertThat(existing.getExtractedText()).isEqualTo("Provided text");
        assertThat(existing.getExtractedAt()).isNotNull();
    }
}