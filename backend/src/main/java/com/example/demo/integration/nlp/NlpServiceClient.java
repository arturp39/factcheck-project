package com.example.demo.integration.nlp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class NlpServiceClient {

    private final RestTemplate restTemplate;

    @Value("${nlp-service.url}")
    private String baseUrl;

    public EmbedResponse embed(List<String> texts, String correlationId) {
        try {
            EmbedRequest req = new EmbedRequest();
            req.setTexts(texts);
            req.setCorrelationId(correlationId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String cid = (correlationId != null && !correlationId.isBlank())
                    ? correlationId
                    : UUID.randomUUID().toString();
            headers.set("X-Correlation-Id", cid);

            HttpEntity<EmbedRequest> entity = new HttpEntity<>(req, headers);

            ResponseEntity<EmbedResponse> resp = restTemplate.exchange(
                    baseUrl + "/embed",
                    HttpMethod.POST,
                    entity,
                    EmbedResponse.class
            );

            if (resp == null || !resp.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException(
                        "NLP embed failed: HTTP status " +
                                (resp != null ? resp.getStatusCode() : "null response")
                );
            }

            EmbedResponse body = resp.getBody();
            if (body == null) {
                throw new RuntimeException("NLP embed failed: empty response body");
            }

            return body;

        } catch (RestClientException e) {
            log.error("NLP embed call failed", e);
            throw new RuntimeException("NLP embed failed", e);
        }
    }

    public float[] embedSingleToVector(String text, String correlationId) {
        EmbedResponse response = embed(Collections.singletonList(text), correlationId);

        if (response.getEmbeddings() == null || response.getEmbeddings().isEmpty()) {
            throw new IllegalStateException("NLP embed response has no embeddings");
        }

        List<Double> first = response.getEmbeddings().getFirst();
        if (first == null || first.isEmpty()) {
            throw new IllegalStateException("First embedding vector is empty");
        }

        float[] vector = new float[first.size()];
        for (int i = 0; i < first.size(); i++) {
            Double v = first.get(i);
            vector[i] = (v != null) ? v.floatValue() : 0.0f;
        }

        log.info("embedSingleToVector() produced vector length={} dimFromService={}",
                vector.length, response.getDimension());
        return vector;
    }
}