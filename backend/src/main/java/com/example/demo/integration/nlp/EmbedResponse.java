package com.example.demo.integration.nlp;

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
