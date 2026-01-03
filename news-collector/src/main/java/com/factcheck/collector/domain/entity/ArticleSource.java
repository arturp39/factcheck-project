package com.factcheck.collector.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "article_sources", schema = "content")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArticleSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "source_endpoint_id", nullable = false)
    private SourceEndpoint sourceEndpoint;

    @Column(name = "source_item_id", nullable = false, columnDefinition = "text")
    private String sourceItemId;

    @Builder.Default
    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt = Instant.now();
}