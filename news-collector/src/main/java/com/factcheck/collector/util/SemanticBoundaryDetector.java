package com.factcheck.collector.util;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SemanticBoundaryDetector {

    public List<Integer> detectBoundaries(
            List<String> sentences,
            List<List<Double>> embeddings,
            double similarityThreshold
    ) {
        if (sentences == null || embeddings == null) {
            throw new IllegalArgumentException("sentences and embeddings are required");
        }
        if (sentences.size() != embeddings.size()) {
            throw new IllegalArgumentException("sentences.size != embeddings.size");
        }

        List<Integer> boundaries = new ArrayList<>();
        boundaries.add(0);

        for (int i = 1; i < embeddings.size(); i++) {
            double sim = cosineSimilarity(embeddings.get(i - 1), embeddings.get(i));
            if (sim < similarityThreshold) {
                boundaries.add(i);
            }
        }

        return boundaries;
    }

    public double cosineSimilarity(List<Double> a, List<Double> b) {
        if (a == null || b == null) throw new IllegalArgumentException("vectors are required");
        if (a.size() != b.size()) throw new IllegalArgumentException("vector dimensions must match");

        double dot = 0.0;
        double na = 0.0;
        double nb = 0.0;

        for (int i = 0; i < a.size(); i++) {
            double x = a.get(i);
            double y = b.get(i);
            dot += x * y;
            na += x * x;
            nb += y * y;
        }

        if (na == 0.0 || nb == 0.0) return 0.0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}