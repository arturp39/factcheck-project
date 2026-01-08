package com.factcheck.collector.repository;

import com.factcheck.collector.domain.entity.Article;
import com.factcheck.collector.domain.entity.Publisher;
import com.factcheck.collector.domain.enums.ArticleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ArticleRepository extends JpaRepository<Article, Long> {

    Optional<Article> findByPublisherAndCanonicalUrlHash(Publisher publisher, String canonicalUrlHash);

    List<Article> findByStatus(ArticleStatus status);

    List<Article> findByPublisherAndStatus(Publisher publisher, ArticleStatus status);

    boolean existsByPublisher(Publisher publisher);

    @Query("""
            select a from Article a
            join fetch a.publisher p
            left join fetch p.mbfcSource
            where a.id = :id
            """)
    Optional<Article> findByIdWithPublisherAndMbfc(@Param("id") Long id);
}
