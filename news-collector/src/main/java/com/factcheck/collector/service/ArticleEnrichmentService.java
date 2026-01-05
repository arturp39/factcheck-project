package com.factcheck.collector.service;

import com.factcheck.collector.domain.entity.Article;
import com.factcheck.collector.domain.entity.ArticleContent;
import com.factcheck.collector.domain.enums.ArticleStatus;
import com.factcheck.collector.integration.fetcher.ArticleContentExtractor;
import com.factcheck.collector.integration.fetcher.ArticleFetchResult;
import com.factcheck.collector.integration.fetcher.RawArticle;
import com.factcheck.collector.repository.ArticleContentRepository;
import com.factcheck.collector.repository.ArticleRepository;
import com.factcheck.collector.util.HashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleEnrichmentService {

    private final ArticleRepository articleRepository;
    private final ArticleContentRepository articleContentRepository;
    private final ArticleContentExtractor contentExtractor;

    @Transactional
    public EnrichmentResult enrich(Article article, RawArticle raw) {
        if (article == null || article.getId() == null) {
            throw new IllegalArgumentException("Article id is required for enrichment");
        }
        String providedText = raw.getRawText();
        ArticleFetchResult fetchResult;

        if (providedText != null && !providedText.isBlank()) {
            fetchResult = ArticleFetchResult.builder()
                    .fetchedAt(Instant.now())
                    .httpStatus(200)
                    .extractedText(providedText)
                    .build();
        } else {
            fetchResult = contentExtractor.fetchAndExtract(article.getCanonicalUrl());
        }

        Article managedArticle = articleRepository.getReferenceById(article.getId());

        if (!applyFetchResult(managedArticle, fetchResult)) {
            return new EnrichmentResult(false, null, fetchResult);
        }

        String extractedText = fetchResult != null ? fetchResult.getExtractedText() : null;
        if (!applyExtractionResult(managedArticle, fetchResult, extractedText)) {
            return new EnrichmentResult(false, null, fetchResult);
        }

        upsertArticleContent(managedArticle, extractedText);
        managedArticle.setContentHash(HashUtils.sha256Hex(extractedText));
        managedArticle.setStatus(ArticleStatus.EXTRACTED);
        articleRepository.save(managedArticle);

        return new EnrichmentResult(true, extractedText, fetchResult);
    }

    private boolean applyFetchResult(Article article, ArticleFetchResult fetchResult) {
        if (fetchResult == null) {
            article.setStatus(ArticleStatus.ERROR);
            article.setFetchError("Fetch failed");
            articleRepository.save(article);
            return false;
        }

        article.setContentFetchedAt(fetchResult.getFetchedAt());
        article.setHttpStatus(fetchResult.getHttpStatus());
        article.setHttpEtag(fetchResult.getHttpEtag());
        article.setHttpLastModified(fetchResult.getHttpLastModified());

        String fetchError = fetchResult.getFetchError();
        Integer status = fetchResult.getHttpStatus();
        if (fetchError != null || status == null || status < 200 || status >= 300) {
            if (fetchError == null && status != null) {
                fetchError = "HTTP status " + status;
            }
            article.setFetchError(fetchError != null ? fetchError : "Fetch failed");
            article.setStatus(ArticleStatus.ERROR);
            articleRepository.save(article);
            return false;
        }

        article.setFetchError(null);
        article.setStatus(ArticleStatus.FETCHED);
        articleRepository.save(article);
        return true;
    }

    private boolean applyExtractionResult(Article article, ArticleFetchResult fetchResult, String extractedText) {
        String extractionError = fetchResult != null ? fetchResult.getExtractionError() : null;
        if (extractionError != null || extractedText == null || extractedText.isBlank()) {
            if (extractionError == null) {
                extractionError = "No meaningful text extracted";
            }
            article.setExtractionError(extractionError);
            article.setStatus(ArticleStatus.ERROR);
            articleRepository.save(article);
            return false;
        }

        article.setExtractionError(null);
        return true;
    }

    private void upsertArticleContent(Article article, String extractedText) {
        ArticleContent content = articleContentRepository.findById(article.getId())
                .orElse(ArticleContent.builder()
                        .article(article)
                        .build());

        content.setExtractedText(extractedText);
        content.setExtractedAt(Instant.now());
        articleContentRepository.save(content);
    }

    public record EnrichmentResult(boolean success, String extractedText, ArticleFetchResult fetchResult) {}
}
