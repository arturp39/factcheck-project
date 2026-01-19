package com.factcheck.collector.util;

import com.factcheck.collector.dto.SemanticChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticChunkingUtilsTest {

    @Test
    void createSemanticChunks_returnsEmptyForNullOrEmptySentences() {
        assertThat(SemanticChunkingUtils.createSemanticChunks(null, List.of(), 1, 2, 100, 0))
                .isEmpty();
        assertThat(SemanticChunkingUtils.createSemanticChunks(List.of(), null, 1, 2, 100, 0))
                .isEmpty();
    }

    @Test
    void createSemanticChunks_splitsOnBoundary() {
        List<String> sentences = List.of("A", "B", "C", "D", "E");

        List<SemanticChunk> chunks = SemanticChunkingUtils.createSemanticChunks(
                sentences,
                List.of(2),
                2,
                10,
                1000,
                0
        );

        assertThat(chunks).hasSize(2);
        SemanticChunk first = chunks.get(0);
        SemanticChunk second = chunks.get(1);

        assertThat(first.text()).isEqualTo("A B");
        assertThat(first.startSentenceIdx()).isEqualTo(0);
        assertThat(first.endSentenceIdx()).isEqualTo(2);
        assertThat(first.sentenceIndices()).containsExactly(0, 1);
        assertThat(first.hasOverlap()).isFalse();

        assertThat(second.text()).isEqualTo("C D E");
        assertThat(second.startSentenceIdx()).isEqualTo(2);
        assertThat(second.endSentenceIdx()).isEqualTo(5);
        assertThat(second.sentenceIndices()).containsExactly(2, 3, 4);
    }

    @Test
    void createSemanticChunks_splitsWhenTokenEstimateTooLarge() {
        String longSentence = "x".repeat(100);
        List<String> sentences = List.of(longSentence, longSentence);

        List<SemanticChunk> chunks = SemanticChunkingUtils.createSemanticChunks(
                sentences,
                List.of(),
                1,
                10,
                10,
                0
        );

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).sentenceIndices()).containsExactly(0);
        assertThat(chunks.get(1).sentenceIndices()).containsExactly(1);
    }

    @Test
    void createSemanticChunks_appliesOverlapWhenConfigured() {
        List<String> sentences = List.of("A", "B", "C", "D");

        List<SemanticChunk> chunks = SemanticChunkingUtils.createSemanticChunks(
                sentences,
                List.of(2),
                2,
                10,
                1000,
                1
        );

        assertThat(chunks).hasSize(2);
        SemanticChunk overlap = chunks.get(1);

        assertThat(overlap.text()).isEqualTo("B C D");
        assertThat(overlap.hasOverlap()).isTrue();
        assertThat(overlap.overlapSentenceCount()).isEqualTo(1);
        assertThat(overlap.sentenceIndices()).containsExactly(1, 2, 3);
    }

    @Test
    void createSemanticChunks_returnsBaseWhenOnlyOneChunk() {
        List<String> sentences = List.of("A", "B");

        List<SemanticChunk> chunks = SemanticChunkingUtils.createSemanticChunks(
                sentences,
                List.of(),
                1,
                10,
                1000,
                1
        );

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).hasOverlap()).isFalse();
    }

    @Test
    void aggregateSentenceEmbeddings_returnsEmptyWhenChunksMissing() {
        assertThat(SemanticChunkingUtils.aggregateSentenceEmbeddings(null, List.of(List.of(1.0))))
                .isEmpty();
        assertThat(SemanticChunkingUtils.aggregateSentenceEmbeddings(List.of(), List.of(List.of(1.0))))
                .isEmpty();
    }

    @Test
    void aggregateSentenceEmbeddings_throwsWhenEmbeddingsMissing() {
        SemanticChunk chunk = new SemanticChunk("A", 0, 1, List.of(0), false, 0);

        assertThatThrownBy(() -> SemanticChunkingUtils.aggregateSentenceEmbeddings(List.of(chunk), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sentenceEmbeddings are required");
    }

    @Test
    void aggregateSentenceEmbeddings_throwsWhenChunkHasNoIndices() {
        SemanticChunk chunk = new SemanticChunk("", 0, 0, List.of(), false, 0);
        List<List<Double>> embeddings = List.of(List.of(1.0));

        assertThatThrownBy(() -> SemanticChunkingUtils.aggregateSentenceEmbeddings(List.of(chunk), embeddings))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot average empty vectors list");
    }

    @Test
    void aggregateSentenceEmbeddings_throwsWhenVectorDimensionsMismatch() {
        SemanticChunk chunk = new SemanticChunk("A B", 0, 2, List.of(0, 1), false, 0);
        List<List<Double>> embeddings = List.of(List.of(1.0, 2.0), List.of(3.0));

        assertThatThrownBy(() -> SemanticChunkingUtils.aggregateSentenceEmbeddings(List.of(chunk), embeddings))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vector dimensions must match");
    }

    @Test
    void aggregateSentenceEmbeddings_returnsAverageVector() {
        SemanticChunk chunk = new SemanticChunk("A B", 0, 2, List.of(0, 1), false, 0);
        List<List<Double>> embeddings = List.of(List.of(1.0, 3.0), List.of(3.0, 1.0));

        List<List<Double>> result = SemanticChunkingUtils.aggregateSentenceEmbeddings(List.of(chunk), embeddings);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsExactly(2.0, 2.0);
    }
}
