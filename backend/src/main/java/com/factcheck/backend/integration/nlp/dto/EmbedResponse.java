package com.factcheck.backend.integration.nlp.dto;

import lombok.Data;

import java.util.List;

@Data
public class EmbedResponse {

    private List<List<Double>> embeddings;

    private Integer dimension;
    private String model;
    private Integer processingTimeMs;
    private String correlationId;
}
