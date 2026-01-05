package com.factcheck.collector.repository;

import com.factcheck.collector.domain.entity.Article;
import com.factcheck.collector.domain.entity.ArticleSource;
import com.factcheck.collector.domain.entity.SourceEndpoint;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface ArticleSourceRepository extends JpaRepository<ArticleSource, Long> {

    boolean existsBySourceEndpointAndSourceItemId(SourceEndpoint sourceEndpoint, String sourceItemId);

    Optional<ArticleSource> findTopByArticleOrderByFetchedAtDesc(Article article);

    @Query("""
            select a.sourceEndpoint.id as sourceEndpointId, count(a) as articleCount
            from ArticleSource a
            where a.sourceEndpoint.id in :sourceEndpointIds
            group by a.sourceEndpoint.id
            """)
    List<SourceEndpointCount> countBySourceEndpointIds(@Param("sourceEndpointIds") List<Long> sourceEndpointIds);

    interface SourceEndpointCount {
        Long getSourceEndpointId();
        long getArticleCount();
    }
}