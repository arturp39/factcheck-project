package com.factcheck.collector.service;

import com.factcheck.collector.domain.entity.Article;
import com.factcheck.collector.domain.entity.ArticleSource;
import com.factcheck.collector.dto.ArticleContentResponse;
import com.factcheck.collector.repository.ArticleRepository;
import com.factcheck.collector.repository.ArticleSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleContentService {

    private final ArticleRepository articleRepository;
    private final ArticleSourceRepository articleSourceRepository;
    private final WeaviateIndexingService weaviateIndexingService;

    public ArticleContentResponse getArticleContent(Long articleId) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new IllegalArgumentException("Article not found: " + articleId));

        // Pull stored chunks from Weaviate to rebuild the article body
        List<String> chunks = weaviateIndexingService.getChunksForArticle(articleId);

        if (chunks.isEmpty()) {
            log.warn("No chunks found in Weaviate for article id={}", articleId);
            throw new IllegalStateException("No content chunks found for article id=" + articleId);
        }

        String fullContent = String.join("\n\n", chunks);

        ArticleSource latestSource = articleSourceRepository.findTopByArticleOrderByFetchedAtDesc(article)
                .orElse(null);

        return ArticleContentResponse.builder()
                .articleId(article.getId())
                .publisherId(article.getPublisher().getId())
                .publisherName(article.getPublisher().getName())
                .sourceEndpointId(latestSource != null ? latestSource.getSourceEndpoint().getId() : null)
                .sourceEndpointName(latestSource != null ? latestSource.getSourceEndpoint().getDisplayName() : null)
                .canonicalUrl(article.getCanonicalUrl())
                .title(article.getTitle())
                .publishedDate(article.getPublishedDate())
                .content(fullContent)
                .build();
    }
}