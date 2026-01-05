package com.factcheck.collector.service;

import com.factcheck.collector.domain.entity.Article;
import com.factcheck.collector.domain.entity.ArticleSource;
import com.factcheck.collector.domain.entity.Publisher;
import com.factcheck.collector.domain.entity.SourceEndpoint;
import com.factcheck.collector.domain.enums.ArticleStatus;
import com.factcheck.collector.integration.fetcher.RawArticle;
import com.factcheck.collector.repository.ArticleRepository;
import com.factcheck.collector.repository.ArticleSourceRepository;
import com.factcheck.collector.util.HashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleDiscoveryService {

    private final ArticleRepository articleRepository;
    private final ArticleSourceRepository articleSourceRepository;

    public DiscoveryResult discover(SourceEndpoint sourceEndpoint, RawArticle raw) {
        String url = raw.getExternalUrl();
        if (url == null || url.isBlank()) {
            log.info("Skipping article with no canonical url");
            return null;
        }

        String sourceItemId = raw.getSourceItemId();
        if (sourceItemId == null || sourceItemId.isBlank()) {
            sourceItemId = url;
        }
        if (sourceItemId == null || sourceItemId.isBlank()) {
            log.info("Skipping article with no source item id: {}", url);
            return null;
        }

        if (articleSourceRepository.existsBySourceEndpointAndSourceItemId(sourceEndpoint, sourceItemId)) {
            log.debug("Article source already exists, skipping sourceItemId={}", sourceItemId);
            return null;
        }

        String canonicalHash = HashUtils.sha256Hex(url);

        Publisher publisher = sourceEndpoint.getPublisher();
        Article article = articleRepository.findByPublisherAndCanonicalUrlHash(publisher, canonicalHash)
                .orElse(null);

        boolean isNew = false;
        if (article == null) {
            article = Article.builder()
                    .publisher(publisher)
                    .originalUrl(raw.getExternalUrl())
                    .canonicalUrl(url)
                    .canonicalUrlHash(canonicalHash)
                    .title(raw.getTitle())
                    .description(raw.getDescription())
                    .publishedDate(raw.getPublishedDate())
                    .status(ArticleStatus.DISCOVERED)
                    .build();

            try {
                article = articleRepository.save(article);
                isNew = true;
            } catch (DataIntegrityViolationException ex) {
                log.info("Duplicate article detected at DB level, skipping url={}", url);
                return null;
            }
        } else {
            article.setLastSeenAt(Instant.now());
            articleRepository.save(article);
        }

        ArticleSource link = ArticleSource.builder()
                .article(article)
                .sourceEndpoint(sourceEndpoint)
                .sourceItemId(sourceItemId)
                .build();

        try {
            articleSourceRepository.save(link);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Duplicate article source link, skipping sourceItemId={}", sourceItemId);
        }

        return new DiscoveryResult(article, isNew);
    }

    public boolean shouldSkip(RawArticle raw) {
        String providedText = raw.getRawText();
        String url = raw.getExternalUrl();
        if (providedText != null && !providedText.isBlank()) {
            return false;
        }
        if (isNonTextMediaPage(url)) {
            log.info("Skipping non-text media page: {}", url);
            return true;
        }
        return false;
    }

    private boolean isNonTextMediaPage(String url) {
        if (url == null) {
            return false;
        }
        String u = url.toLowerCase();

        return u.contains("/video/")
                || u.contains("/videos/")
                || u.contains("/newsfeed/")
                || u.contains("/latest-news-bulletin")
                || u.contains("/picture/")
                || u.contains("/cartoon/")
                || u.contains("/gallery/")
                || u.contains("/slideshow/")
                || u.contains("/watch/")
                || u.contains("/live/")
                || u.contains("/iplayer/");
    }

    public record DiscoveryResult(Article article, boolean isNew) {}
}
