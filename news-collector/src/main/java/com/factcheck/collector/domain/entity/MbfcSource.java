package com.factcheck.collector.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "mbfc_sources", schema = "content")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MbfcSource {

    @Id
    @Column(name = "mbfc_source_id")
    private Long mbfcSourceId;

    @Column(name = "source_name", nullable = false, columnDefinition = "text")
    private String sourceName;

    @Column(name = "mbfc_url", nullable = false, columnDefinition = "text")
    private String mbfcUrl;

    @Column(name = "bias", columnDefinition = "text")
    private String bias;

    @Column(name = "country", columnDefinition = "text")
    private String country;

    @Column(name = "factual_reporting", columnDefinition = "text")
    private String factualReporting;

    @Column(name = "media_type", columnDefinition = "text")
    private String mediaType;

    @Column(name = "source_url", columnDefinition = "text")
    private String sourceUrl;

    @Column(name = "source_url_domain", columnDefinition = "text")
    private String sourceUrlDomain;

    @Column(name = "credibility", columnDefinition = "text")
    private String credibility;

    @Builder.Default
    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt = Instant.now();
}