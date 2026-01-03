package com.factcheck.collector.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "publishers", schema = "content")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Publisher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "language_code", length = 10)
    private String languageCode;

    @Column(name = "bias_label", length = 100)
    private String biasLabel;

    @Builder.Default
    @Column(name = "reliability_score", nullable = false)
    private double reliabilityScore = 0.50;

    @Column(name = "website_url", columnDefinition = "text")
    private String websiteUrl;

    @Column(name = "mbfc_url", columnDefinition = "text")
    private String mbfcUrl;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }
}