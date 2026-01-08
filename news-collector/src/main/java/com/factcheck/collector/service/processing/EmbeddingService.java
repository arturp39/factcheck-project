package com.factcheck.collector.service.processing;

import com.factcheck.collector.integration.nlp.NlpServiceClient;
import com.factcheck.collector.integration.nlp.dto.EmbedRequest;
import com.factcheck.collector.integration.nlp.dto.EmbedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private static final int DEFAULT_MAX_TEXTS_PER_REQUEST = 100;

    private final NlpServiceClient nlpClient;

    @Value("${nlp.max-texts-per-request:" + DEFAULT_MAX_TEXTS_PER_REQUEST + "}")
    private int maxTextsPerRequest = DEFAULT_MAX_TEXTS_PER_REQUEST;

    public List<List<Double>> embedChunks(List<String> chunks, String correlationId) {
        log.info("Requesting embeddings for {} chunks, correlationId={}", chunks.size(), correlationId);

        if (chunks.size() > maxTextsPerRequest) {
            throw new IllegalArgumentException(
                    "Too many chunks for embedding (" + chunks.size() + " > " + maxTextsPerRequest + ")"
            );
        }

        // Preserve correlation id so NLP logs can be traced.
        EmbedRequest req = new EmbedRequest();
        req.setTexts(chunks);
        req.setCorrelationId(correlationId);

        EmbedResponse resp = nlpClient.embed(req);
        return resp.getEmbeddings();
    }
}