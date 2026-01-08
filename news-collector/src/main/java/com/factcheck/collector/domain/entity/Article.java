package com.factcheck.collector.domain.entity;

import com.factcheck.collector.domain.enums.ArticleStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "articles", schema = "content")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "publisher_id", nullable = false)
    private Publisher publisher;

    @Column(name = "canonical_url", nullable = false, columnDefinition = "text")
    private String canonicalUrl;

    @Column(name = "canonical_url_hash", nullable = false, length = 64)
    private String canonicalUrlHash;

    @Column(name = "original_url", columnDefinition = "text")
    private String originalUrl;

    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "published_date")
    private Instant publishedDate;

    @Builder.Default
    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt = Instant.now();

    @Builder.Default
    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt = Instant.now();

    @Column(name = "content_fetched_at")
    private Instant contentFetchedAt;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "http_etag", columnDefinition = "text")
    private String httpEtag;

    @Column(name = "http_last_modified", columnDefinition = "text")
    private String httpLastModified;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Builder.Default
    @Column(name = "chunk_count", nullable = false)
    private int chunkCount = 0;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private ArticleStatus status = ArticleStatus.DISCOVERED;

    @Column(name = "fetch_error", columnDefinition = "text")
    private String fetchError;

    @Column(name = "extraction_error", columnDefinition = "text")
    private String extractionError;

    @Builder.Default
    @Column(name = "weaviate_indexed", nullable = false)
    private boolean weaviateIndexed = false;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}