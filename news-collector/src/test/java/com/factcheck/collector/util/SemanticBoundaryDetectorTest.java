package com.factcheck.collector.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticBoundaryDetectorTest {

    private final SemanticBoundaryDetector detector = new SemanticBoundaryDetector();

    @Test
    void detectBoundaries_throwsWhenSentencesNull() {
        assertThatThrownBy(() -> detector.detectBoundaries(null, List.of(List.of(1.0)), 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sentences and embeddings are required");
    }

    @Test
    void detectBoundaries_throwsWhenEmbeddingsNull() {
        assertThatThrownBy(() -> detector.detectBoundaries(List.of("a"), null, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sentences and embeddings are required");
    }

    @Test
    void detectBoundaries_throwsWhenSizesMismatch() {
        assertThatThrownBy(() -> detector.detectBoundaries(List.of("a", "b"), List.of(List.of(1.0)), 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sentences.size != embeddings.size");
    }

    @Test
    void detectBoundaries_addsBoundaryWhenSimilarityBelowThreshold() {
        List<String> sentences = List.of("A", "B", "C");
        List<List<Double>> embeddings = List.of(
                List.of(1.0, 0.0),
                List.of(1.0, 0.0),
                List.of(0.0, 1.0)
        );

        List<Integer> boundaries = detector.detectBoundaries(sentences, embeddings, 0.5);

        assertThat(boundaries).containsExactly(0, 2);
    }

    @Test
    void cosineSimilarity_throwsWhenVectorsNull() {
        assertThatThrownBy(() -> detector.cosineSimilarity(null, List.of(1.0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vectors are required");
    }

    @Test
    void cosineSimilarity_throwsWhenDimensionsMismatch() {
        assertThatThrownBy(() -> detector.cosineSimilarity(List.of(1.0, 2.0), List.of(1.0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vector dimensions must match");
    }

    @Test
    void cosineSimilarity_returnsZeroWhenZeroVector() {
        double result = detector.cosineSimilarity(List.of(0.0, 0.0), List.of(1.0, 0.0));

        assertThat(result).isZero();
    }

    @Test
    void cosineSimilarity_returnsExpectedValue() {
        double result = detector.cosineSimilarity(List.of(1.0, 0.0), List.of(1.0, 0.0));

        assertThat(result).isEqualTo(1.0);
    }
}
