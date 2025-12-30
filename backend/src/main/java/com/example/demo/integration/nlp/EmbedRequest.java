package com.example.demo.integration.nlp;

import lombok.Data;

import java.util.List;

@Data
public class EmbedRequest {

    private List<String> texts;

    private String correlationId;
}
