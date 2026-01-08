package com.factcheck.collector.service.read;

import com.factcheck.collector.domain.entity.Article;
import com.factcheck.collector.domain.entity.ArticleContent;
import com.factcheck.collector.domain.entity.ArticleSource;
import com.factcheck.collector.dto.ArticleContentResponse;
import com.factcheck.collector.repository.ArticleContentRepository;
import com.factcheck.collector.repository.ArticleRepository;
import com.factcheck.collector.repository.ArticleSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ArticleContentService {

    private final ArticleRepository articleRepository;
    private final ArticleContentRepository articleContentRepository;
    private final ArticleSourceRepository articleSourceRepository;

    public ArticleContentResponse getArticleContent(Long articleId) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new IllegalArgumentException("Article not found: " + articleId));

        ArticleContent content = articleContentRepository.findByArticle(article)
                .orElseThrow(() -> new IllegalStateException("No content found for article id=" + articleId));

        ArticleSource latestSource = articleSourceRepository.findTopByArticleOrderByFetchedAtDesc(article)
                .orElse(null);

        return new ArticleContentResponse(
                article.getId(),
                article.getPublisher().getId(),
                article.getPublisher().getName(),
                latestSource != null ? latestSource.getSourceEndpoint().getId() : null,
                latestSource != null ? latestSource.getSourceEndpoint().getDisplayName() : null,
                article.getCanonicalUrl(),
                article.getTitle(),
                article.getPublishedDate(),
                content.getExtractedText()
        );
    }
}