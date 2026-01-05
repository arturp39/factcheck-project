package com.factcheck.collector.service;

import com.factcheck.collector.domain.entity.Article;
import com.factcheck.collector.domain.entity.ArticleSource;
import com.factcheck.collector.dto.ArticleMetadataResponse;
import com.factcheck.collector.repository.ArticleRepository;
import com.factcheck.collector.repository.ArticleSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ArticleMetadataService {

    private final ArticleRepository articleRepository;
    private final ArticleSourceRepository articleSourceRepository;

    public ArticleMetadataResponse getArticleMetadata(Long id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Article not found: " + id));

        ArticleSource latestSource = articleSourceRepository.findTopByArticleOrderByFetchedAtDesc(article)
                .orElse(null);

        return new ArticleMetadataResponse(
                article.getId(),
                article.getPublisher().getId(),
                article.getPublisher().getName(),
                latestSource != null ? latestSource.getSourceEndpoint().getId() : null,
                latestSource != null ? latestSource.getSourceEndpoint().getDisplayName() : null,
                article.getCanonicalUrl(),
                article.getTitle(),
                article.getPublishedDate(),
                article.getChunkCount(),
                article.getStatus().name(),
                article.isWeaviateIndexed()
        );
    }
}
