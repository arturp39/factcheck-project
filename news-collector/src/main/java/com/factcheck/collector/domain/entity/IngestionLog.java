package com.factcheck.collector.domain.entity;

import com.factcheck.collector.domain.enums.IngestionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ingestion_logs", schema = "content")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngestionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_endpoint_id", nullable = false)
    private SourceEndpoint sourceEndpoint;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "run_id", nullable = true)
    private IngestionRun run;

    @Builder.Default
    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @Builder.Default
    @Column(name = "articles_fetched", nullable = false)
    private int articlesFetched = 0;

    @Builder.Default
    @Column(name = "articles_processed", nullable = false)
    private int articlesProcessed = 0;

    @Builder.Default
    @Column(name = "articles_failed", nullable = false)
    private int articlesFailed = 0;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(length = 50)
    private IngestionStatus status;

    @Column(name = "error_details", columnDefinition = "text")
    private String errorDetails;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;
}