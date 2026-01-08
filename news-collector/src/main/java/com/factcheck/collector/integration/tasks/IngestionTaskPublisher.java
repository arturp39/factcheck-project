package com.factcheck.collector.integration.tasks;

import com.factcheck.collector.dto.IngestionTaskRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Slf4j
@Profile("gcp")
@Component
@RequiredArgsConstructor
public class IngestionTaskPublisher implements TaskPublisher {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    private final ObjectMapper mapper;

    // Single HttpClient per bean.
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${cloud-tasks.project-id:}")
    private String projectId;

    @Value("${cloud-tasks.location:}")
    private String location;

    @Value("${cloud-tasks.queue:}")
    private String queue;

    @Value("${cloud-tasks.target-url:}")
    private String targetUrl;

    @Value("${cloud-tasks.service-account-email:}")
    private String serviceAccountEmail;

    @Value("${cloud-tasks.access-token:}")
    private String accessTokenOverride;

    @Value("${cloud-tasks.metadata-url:http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token}")
    private String metadataUrl;

    private volatile CachedToken cachedToken;

    @Override
    public void enqueueIngestionTask(IngestionTaskRequest taskRequest) {
        validateConfig();

        if (taskRequest == null || taskRequest.runId() == null || taskRequest.sourceEndpointId() == null) {
            throw new IllegalArgumentException("runId and sourceEndpointId are required");
        }

        try {
            String queuePath = String.format("projects/%s/locations/%s/queues/%s", projectId, location, queue);
            String url = "https://cloudtasks.googleapis.com/v2/" + queuePath + "/tasks";

            String payload = mapper.writeValueAsString(taskRequest);
            String base64Body = Base64.getEncoder()
                    .encodeToString(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            var root = mapper.createObjectNode();
            var taskNode = root.putObject("task");
            var httpRequest = taskNode.putObject("httpRequest");
            httpRequest.put("httpMethod", "POST");
            httpRequest.put("url", targetUrl);
            httpRequest.put("body", base64Body);

            var headers = httpRequest.putObject("headers");
            headers.put("Content-Type", "application/json");
            if (taskRequest.correlationId() != null && !taskRequest.correlationId().isBlank()) {
                headers.put("X-Correlation-Id", taskRequest.correlationId());
            }

            if (serviceAccountEmail != null && !serviceAccountEmail.isBlank()) {
                var oidc = httpRequest.putObject("oidcToken");
                oidc.put("serviceAccountEmail", serviceAccountEmail);
            }

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + getAccessToken())
                    .timeout(HTTP_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(root)))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Cloud Tasks enqueue failed HTTP " + resp.statusCode() + ": " + resp.body()
                );
            }
        } catch (Exception e) {
            log.error("Failed to enqueue Cloud Task for runId={} sourceEndpointId={}",
                    taskRequest.runId(), taskRequest.sourceEndpointId(), e);
            throw new IllegalStateException("Cloud Tasks enqueue failed", e);
        }
    }

    private void validateConfig() {
        if (isBlank(projectId) || isBlank(location) || isBlank(queue) || isBlank(targetUrl)) {
            throw new IllegalStateException("Cloud Tasks config missing (project-id, location, queue, target-url)");
        }
    }

    private String getAccessToken() throws Exception {
        if (!isBlank(accessTokenOverride)) {
            return accessTokenOverride;
        }

        CachedToken cached = cachedToken;
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt.isAfter(now.plusSeconds(30))) {
            return cached.token;
        }

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(metadataUrl))
                .header("Metadata-Flavor", "Google")
                .timeout(HTTP_TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IllegalStateException("Metadata token fetch failed HTTP " + resp.statusCode());
        }

        JsonNode root = mapper.readTree(resp.body());
        String token = root.path("access_token").asText(null);
        long expiresIn = root.path("expires_in").asLong(0);

        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Metadata token fetch returned empty token");
        }

        Instant expiresAt = now.plusSeconds(expiresIn > 0 ? expiresIn : 300);
        cachedToken = new CachedToken(token, expiresAt);
        return token;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record CachedToken(String token, Instant expiresAt) {
    }
}