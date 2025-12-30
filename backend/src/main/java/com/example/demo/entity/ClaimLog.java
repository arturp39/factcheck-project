package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "claim_log")
@Data
public class ClaimLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_text", columnDefinition = "TEXT", nullable = false)
    private String claimText;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "model_answer", columnDefinition = "TEXT")
    private String modelAnswer;

    private String verdict;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "bias_analysis", columnDefinition = "TEXT")
    private String biasAnalysis;
}