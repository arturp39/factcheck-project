package com.factcheck.collector.repository;

import com.factcheck.collector.domain.entity.Article;
import com.factcheck.collector.domain.entity.ArticleContent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ArticleContentRepository extends JpaRepository<ArticleContent, Long> {

    Optional<ArticleContent> findByArticle(Article article);
}
