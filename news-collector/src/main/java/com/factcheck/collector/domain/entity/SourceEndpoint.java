package com.factcheck.collector.domain.entity;

import com.factcheck.collector.domain.enums.SourceKind;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "source_endpoints", schema = "content")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SourceEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "publisher_id", nullable = false)
    private Publisher publisher;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, length = 10)
    private SourceKind kind;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "rss_url", columnDefinition = "text")
    private String rssUrl;

    @Column(name = "api_provider", length = 100)
    private String apiProvider;

    @Column(name = "api_query", columnDefinition = "text")
    private String apiQuery;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    @Builder.Default
    @Column(name = "fetch_interval_minutes", nullable = false)
    private int fetchIntervalMinutes = 1440;

    @Column(name = "last_fetched_at")
    private Instant lastFetchedAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Builder.Default
    @Column(name = "failure_count", nullable = false)
    private int failureCount = 0;

    @Builder.Default
    @Column(name = "robots_disallowed", nullable = false)
    private boolean robotsDisallowed = false;

    @Column(name = "blocked_until")
    private Instant blockedUntil;

    @Column(name = "block_reason", columnDefinition = "text")
    private String blockReason;

    @Builder.Default
    @Column(name = "block_count", nullable = false)
    private int blockCount = 0;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}