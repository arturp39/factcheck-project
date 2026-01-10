package com.factcheck.collector.service.read;

import com.factcheck.collector.domain.entity.Article;
import com.factcheck.collector.domain.entity.ArticleSource;
import com.factcheck.collector.dto.ArticleMetadataResponse;
import com.factcheck.collector.repository.ArticleRepository;
import com.factcheck.collector.repository.ArticleSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ArticleListService {

    private final ArticleRepository articleRepository;
    private final ArticleSourceRepository articleSourceRepository;

    public List<ArticleMetadataResponse> listLatest(int limit) {
        int safeLimit = clampLimit(limit);
        List<Article> articles = articleRepository.findLatest(safeLimit);
        return articles.stream().map(this::toMetadataResponse).toList();
    }

    public List<ArticleMetadataResponse> searchByTitle(String q, int limit) {
        int safeLimit = clampLimit(limit);
        String query = (q == null) ? "" : q.trim();
        if (query.isEmpty()) {
            return listLatest(safeLimit);
        }
        List<Article> articles = articleRepository.searchByTitle(query, safeLimit);
        return articles.stream().map(this::toMetadataResponse).toList();
    }

    private int clampLimit(int limit) {
        if (limit <= 0) return 50;
        return Math.min(limit, 200);
    }

    private ArticleMetadataResponse toMetadataResponse(Article article) {
        ArticleSource latestSource = articleSourceRepository
                .findTopByArticleOrderByFetchedAtDesc(article)
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