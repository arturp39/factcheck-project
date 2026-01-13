package com.factcheck.collector.integration.nlp.dto;

import lombok.Data;

import java.util.List;

@Data
public class SentenceEmbedRequest {
    private List<String> sentences;
    private String correlationId;
}