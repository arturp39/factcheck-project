package com.factcheck.collector.dto;

import java.util.List;

public record SemanticChunk(
        String text,
        int startSentenceIdx,
        int endSentenceIdx,
        List<Integer> sentenceIndices,
        boolean hasOverlap,
        int overlapSentenceCount
) {
}