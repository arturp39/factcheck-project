package com.factcheck.collector.repository;

import com.factcheck.collector.domain.entity.Article;
import com.factcheck.collector.domain.entity.ArticleSource;
import com.factcheck.collector.domain.entity.SourceEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ArticleSourceRepository extends JpaRepository<ArticleSource, Long> {

    boolean existsBySourceEndpointAndSourceItemId(SourceEndpoint sourceEndpoint, String sourceItemId);

    Optional<ArticleSource> findTopByArticleOrderByFetchedAtDesc(Article article);
}