package com.factcheck.collector.util;

import com.factcheck.collector.dto.SemanticChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SemanticChunkingUtils {

    private SemanticChunkingUtils() {
    }

    public static List<SemanticChunk> createSemanticChunks(
            List<String> sentences,
            List<Integer> boundaries,
            int minSentences,
            int maxSentences,
            int maxTokens,
            int overlapSentences
    ) {
        if (sentences == null || sentences.isEmpty()) return List.of();
        if (boundaries == null) boundaries = List.of();

        var boundarySet = new java.util.HashSet<>(boundaries);

        List<SemanticChunk> baseChunks = new ArrayList<>();
        int start = 0;

        for (int i = 1; i <= sentences.size(); i++) {
            int sentenceCount = i - start;
            int tokenEstimate = estimateTokens(sentences, start, i);

            boolean atSemanticBoundary = boundarySet.contains(i);
            boolean chunkLargeEnough = sentenceCount >= minSentences;
            boolean tooLarge = sentenceCount >= maxSentences || tokenEstimate > maxTokens;

            if (chunkLargeEnough && (atSemanticBoundary || tooLarge)) {
                baseChunks.add(buildChunk(sentences, start, i, false, 0));
                start = i;
            }
        }

        if (start < sentences.size()) {
            baseChunks.add(buildChunk(sentences, start, sentences.size(), false, 0));
        }

        if (overlapSentences <= 0 || baseChunks.size() <= 1) {
            return baseChunks;
        }

        return applyOverlap(sentences, baseChunks, overlapSentences);
    }

    public static List<List<Double>> aggregateSentenceEmbeddings(
            List<SemanticChunk> chunks,
            List<List<Double>> sentenceEmbeddings
    ) {
        if (chunks == null || chunks.isEmpty()) return List.of();
        if (sentenceEmbeddings == null || sentenceEmbeddings.isEmpty()) {
            throw new IllegalArgumentException("sentenceEmbeddings are required");
        }

        List<List<Double>> out = new ArrayList<>(chunks.size());
        for (SemanticChunk c : chunks) {
            List<List<Double>> vecs = new ArrayList<>(c.sentenceIndices().size());
            for (int idx : c.sentenceIndices()) {
                vecs.add(sentenceEmbeddings.get(idx));
            }
            out.add(averageVectors(vecs));
        }
        return out;
    }

    private static SemanticChunk buildChunk(
            List<String> sentences,
            int startIdx,
            int endIdx,
            boolean hasOverlap,
            int overlapSentenceCount
    ) {
        StringBuilder sb = new StringBuilder();
        List<Integer> indices = new ArrayList<>(Math.max(0, endIdx - startIdx));

        for (int i = startIdx; i < endIdx; i++) {
            if (i > startIdx) sb.append(' ');
            sb.append(sentences.get(i));
            indices.add(i);
        }

        return new SemanticChunk(
                sb.toString(),
                startIdx,
                endIdx,
                List.copyOf(indices),
                hasOverlap,
                overlapSentenceCount
        );
    }

    private static List<SemanticChunk> applyOverlap(
            List<String> sentences,
            List<SemanticChunk> base,
            int overlapSentences
    ) {
        List<SemanticChunk> out = new ArrayList<>(base.size());
        out.add(base.get(0));

        for (int i = 1; i < base.size(); i++) {
            SemanticChunk prev = out.get(i - 1);
            SemanticChunk cur = base.get(i);

            int overlapStart = Math.max(cur.startSentenceIdx() - overlapSentences, prev.startSentenceIdx());
            int overlapEnd = cur.startSentenceIdx();

            if (overlapStart >= overlapEnd) {
                out.add(cur);
                continue;
            }

            // Build new chunk text with overlapped sentences + original chunk text.
            StringBuilder sb = new StringBuilder();
            List<Integer> newIdx = new ArrayList<>();

            for (int s = overlapStart; s < overlapEnd; s++) {
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(sentences.get(s));
                newIdx.add(s);
            }

            if (!sb.isEmpty()) sb.append(' ');
            sb.append(cur.text());
            newIdx.addAll(cur.sentenceIndices());

            out.add(new SemanticChunk(
                    sb.toString(),
                    overlapStart,
                    cur.endSentenceIdx(),
                    List.copyOf(newIdx),
                    true,
                    overlapEnd - overlapStart
            ));
        }

        return out;
    }

    private static int estimateTokens(List<String> sentences, int start, int end) {
        int chars = 0;
        for (int i = start; i < end; i++) chars += sentences.get(i).length();
        return chars / 4;
    }

    private static List<Double> averageVectors(List<List<Double>> vectors) {
        if (vectors == null || vectors.isEmpty()) {
            throw new IllegalArgumentException("cannot average empty vectors list");
        }

        int dim = vectors.get(0).size();
        List<Double> avg = new ArrayList<>(Collections.nCopies(dim, 0.0));

        for (List<Double> v : vectors) {
            if (v.size() != dim) throw new IllegalArgumentException("vector dimensions must match");
            for (int i = 0; i < dim; i++) {
                avg.set(i, avg.get(i) + v.get(i));
            }
        }

        for (int i = 0; i < dim; i++) {
            avg.set(i, avg.get(i) / vectors.size());
        }

        return avg;
    }
}