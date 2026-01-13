package com.factcheck.collector.dto;

import java.util.List;

public record ChunkingResult(
        List<String> chunks,
        List<List<Double>> embeddings,
        boolean semanticUsed
) {
    public boolean hasPrecomputedEmbeddings() {
        return embeddings != null && !embeddings.isEmpty();
    }
}