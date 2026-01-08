package com.factcheck.collector.service.ingestion.pipeline;

import com.factcheck.collector.domain.entity.Article;
import com.factcheck.collector.domain.enums.ArticleStatus;
import com.factcheck.collector.repository.ArticleRepository;
import com.factcheck.collector.service.processing.ArticleProcessingService;
import com.factcheck.collector.service.processing.EmbeddingService;
import com.factcheck.collector.service.processing.WeaviateIndexingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleIndexingService {

    private final ArticleProcessingService articleProcessingService;
    private final EmbeddingService embeddingService;
    private final WeaviateIndexingService weaviateIndexingService;
    private final ArticleRepository articleRepository;

    public boolean index(Article article, String fullText, String correlationId) {
        try {
            Article resolved = resolveForIndexing(article);
            var chunks = articleProcessingService.createChunks(resolved, fullText, correlationId);
            var embeddings = embeddingService.embedChunks(chunks, correlationId);
            weaviateIndexingService.indexArticleChunks(resolved, chunks, embeddings, correlationId);

            resolved.setChunkCount(chunks.size());
            resolved.setWeaviateIndexed(true);
            resolved.setStatus(ArticleStatus.INDEXED);
            articleRepository.save(resolved);
            return true;

        } catch (Exception e) {
            log.error("Processing/indexing failed for article id={}", article != null ? article.getId() : null, e);
            if (article != null) {
                article.setWeaviateIndexed(false);
                article.setStatus(ArticleStatus.ERROR);
                article.setExtractionError("Indexing failed: " + e.getMessage());
                articleRepository.save(article);
            }
            return false;
        }
    }

    private Article resolveForIndexing(Article article) {
        if (article == null || article.getId() == null) {
            return article;
        }
        return articleRepository.findByIdWithPublisherAndMbfc(article.getId()).orElse(article);
    }
}
