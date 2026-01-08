package com.factcheck.collector.integration.tasks;

import com.factcheck.collector.dto.IngestionTaskRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Profile("!gcp")
@Component
@Slf4j
public class LocalTaskPublisher implements TaskPublisher {

    private final RestTemplate restTemplate;
    private final String localTaskUrl;

    public LocalTaskPublisher(
            RestTemplate restTemplate,
            @Value("${local-tasks.target-url:http://localhost:8081/ingestion/task}") String localTaskUrl
    ) {
        this.restTemplate = restTemplate;
        this.localTaskUrl = localTaskUrl;
    }

    @Override
    public void enqueueIngestionTask(IngestionTaskRequest taskRequest) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (taskRequest.correlationId() != null && !taskRequest.correlationId().isBlank()) {
                headers.set("X-Correlation-Id", taskRequest.correlationId());
            }

            HttpEntity<IngestionTaskRequest> entity = new HttpEntity<>(taskRequest, headers);
            ResponseEntity<Void> resp = restTemplate.exchange(
                    localTaskUrl,
                    HttpMethod.POST,
                    entity,
                    Void.class
            );

            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("Local task call failed HTTP " + resp.getStatusCode());
            }
        } catch (RestClientException e) {
            log.error("Failed local task call runId={} sourceEndpointId={}",
                    taskRequest.runId(), taskRequest.sourceEndpointId(), e);
            throw new IllegalStateException("Local task enqueue failed", e);
        }
    }
}