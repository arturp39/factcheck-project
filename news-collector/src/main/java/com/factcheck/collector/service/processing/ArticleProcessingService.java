package com.factcheck.collector.service.processing;

import com.factcheck.collector.domain.entity.Article;
import com.factcheck.collector.dto.ChunkingResult;
import com.factcheck.collector.dto.SemanticChunk;
import com.factcheck.collector.integration.nlp.NlpServiceClient;
import com.factcheck.collector.integration.nlp.dto.PreprocessResponse;
import com.factcheck.collector.integration.nlp.dto.SentenceEmbedRequest;
import com.factcheck.collector.integration.nlp.dto.SentenceEmbedResponse;
import com.factcheck.collector.util.ChunkingUtils;
import com.factcheck.collector.util.SemanticBoundaryDetector;
import com.factcheck.collector.util.SemanticChunkingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleProcessingService {

    private final NlpServiceClient nlpClient;
    private final SemanticBoundaryDetector boundaryDetector;

    @Value("${chunking.use-semantic:true}")
    private boolean useSemanticChunking;

    @Value("${chunking.similarity-threshold:0.65}")
    private double similarityThreshold;

    @Value("${chunking.min-sentences:2}")
    private int minSentences;

    @Value("${chunking.max-sentences:8}")
    private int maxSentences;

    @Value("${chunking.max-tokens:400}")
    private int maxTokens;

    @Value("${chunking.overlap-sentences:1}")
    private int overlapSentences;

    @Value("${chunking.semantic-min-sentences:10}")
    private int semanticMinSentences;

    @Value("${chunking.semantic-max-sentences-per-request:100}")
    private int semanticMaxSentencesPerRequest;

    public ChunkingResult createChunks(Article article, String fullText, String correlationId) {
        PreprocessResponse response = nlpClient.preprocess(fullText, correlationId);
        List<String> sentences = response.getSentences();

        if (!useSemanticChunking || sentences.size() < semanticMinSentences) {
            List<String> chunks = ChunkingUtils.chunkSentences(sentences);
            return new ChunkingResult(chunks, null, false);
        }

        try {
            List<List<Double>> sentenceEmbeddings = embedSentencesBatched(sentences, correlationId);

            List<Integer> boundaries = boundaryDetector.detectBoundaries(
                    sentences,
                    sentenceEmbeddings,
                    similarityThreshold
            );

            List<SemanticChunk> semanticChunks = SemanticChunkingUtils.createSemanticChunks(
                    sentences,
                    boundaries,
                    minSentences,
                    maxSentences,
                    maxTokens,
                    overlapSentences
            );

            List<String> chunkTexts = semanticChunks.stream().map(SemanticChunk::text).toList();
            List<List<Double>> chunkEmbeddings = SemanticChunkingUtils.aggregateSentenceEmbeddings(
                    semanticChunks,
                    sentenceEmbeddings
            );

            return new ChunkingResult(chunkTexts, chunkEmbeddings, true);

        } catch (Exception e) {
            log.error("Semantic chunking failed, falling back to simple chunking for article id={}",
                    article != null ? article.getId() : null, e);
            List<String> chunks = ChunkingUtils.chunkSentences(sentences);
            return new ChunkingResult(chunks, null, false);
        }
    }

    private List<List<Double>> embedSentencesBatched(List<String> sentences, String correlationId) {
        if (sentences == null || sentences.isEmpty()) {
            throw new IllegalArgumentException("Sentences are required for semantic chunking");
        }

        int batchSize = Math.max(1, semanticMaxSentencesPerRequest);
        if (sentences.size() > batchSize) {
            log.info(
                    "Embedding sentences in {} batches (total sentences={})",
                    (int) Math.ceil((double) sentences.size() / batchSize),
                    sentences.size()
            );
        }

        List<List<Double>> allEmbeddings = new java.util.ArrayList<>(sentences.size());
        for (int i = 0; i < sentences.size(); i += batchSize) {
            int end = Math.min(i + batchSize, sentences.size());
            List<String> batch = sentences.subList(i, end);

            SentenceEmbedRequest embedReq = new SentenceEmbedRequest();
            embedReq.setSentences(batch);
            embedReq.setCorrelationId(correlationId);

            SentenceEmbedResponse embedResp = nlpClient.embedSentences(embedReq);
            List<List<Double>> batchEmbeddings = embedResp.getEmbeddings();
            if (batchEmbeddings == null || batchEmbeddings.size() != batch.size()) {
                throw new IllegalStateException(
                        "Sentence embedding count mismatch: expected " + batch.size()
                                + " got " + (batchEmbeddings == null ? 0 : batchEmbeddings.size())
                );
            }
            allEmbeddings.addAll(batchEmbeddings);
        }

        return allEmbeddings;
    }
}
