package com.example.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.http.HttpResponse;

@Slf4j
@Service
public class VertexEmbeddingService {

    private final VertexAuthHelper authHelper;
    private final VertexApiClient vertexApiClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public VertexEmbeddingService(VertexAuthHelper authHelper,
                                  VertexApiClient vertexApiClient) {
        this.authHelper = authHelper;
        this.vertexApiClient = vertexApiClient;
    }

    public float[] embedText(String text) {
        try {
            log.info("Requesting embedding for text length={}", text.length());

            String endpoint = authHelper.embeddingEndpoint();
            // Build a Vertex embedding request
            String requestBody = buildRequestBody(text);

            HttpResponse<String> resp = vertexApiClient.postJson(endpoint, requestBody);

            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.error("Embedding error status={} body={}", resp.statusCode(), resp.body());
                throw new RuntimeException("Embedding error " + resp.statusCode());
            }

            float[] vector = extractVector(resp.body());
            log.info("Received embedding vector length={}", vector.length);
            return vector;
        } catch (Exception e) {
            log.error("embedText() failed", e);
            throw new RuntimeException("Embedding failed: " + e.getMessage(), e);
        }
    }

    private String buildRequestBody(String text) throws Exception {
        var root = mapper.createObjectNode();
        var instances = root.putArray("instances");
        var inst = instances.addObject();
        inst.put("content", text);
        return mapper.writeValueAsString(root);
    }

    private float[] extractVector(String responseBody) throws Exception {
        JsonNode root = mapper.readTree(responseBody);
        JsonNode predictions = root.path("predictions");
        if (!predictions.isArray() || predictions.isEmpty()) {
            throw new IllegalStateException("No predictions field in embedding response");
        }

        JsonNode values = predictions.get(0).path("embeddings").path("values");
        if (!values.isArray() || values.isEmpty()) {
            throw new IllegalStateException("No embeddings.values field in embedding response");
        }

        float[] vector = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            vector[i] = (float) values.get(i).asDouble();
        }
        return vector;
    }
}
