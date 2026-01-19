package com.factcheck.collector.integration.nlp;

import com.factcheck.collector.exception.NlpServiceException;
import com.factcheck.collector.integration.nlp.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class NlpServiceClient {

    private final RestTemplate restTemplate;
    private final NlpServiceAuthTokenProvider authTokenProvider;

    @Value("${nlp-service.url}")
    private String baseUrl;

    @Value("${nlp-service.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${nlp-service.retry.initial-backoff-ms:500}")
    private long retryInitialBackoffMs;

    @Value("${nlp-service.retry.max-backoff-ms:5000}")
    private long retryMaxBackoffMs;

    private static final Set<Integer> RETRY_STATUS_CODES = Set.of(
            HttpStatus.TOO_MANY_REQUESTS.value(),
            HttpStatus.SERVICE_UNAVAILABLE.value()
    );

    public PreprocessResponse preprocess(String text, String correlationId) {
        try {
            PreprocessRequest req = new PreprocessRequest();
            req.setText(text);
            req.setCorrelationId(correlationId);

            HttpHeaders headers = buildHeaders(correlationId);
            String resolvedCorrelationId = resolveCorrelationId(headers);

            HttpEntity<PreprocessRequest> entity = new HttpEntity<>(req, headers);

            ResponseEntity<PreprocessResponse> resp = exchangeWithRetry(
                    baseUrl + "/preprocess",
                    entity,
                    PreprocessResponse.class,
                    "preprocess",
                    resolvedCorrelationId
            );

            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new NlpServiceException(
                        "NLP preprocess failed: HTTP status " +
                                resp.getStatusCode().value()
                );
            }

            PreprocessResponse body = resp.getBody();
            if (body == null) {
                throw new NlpServiceException("NLP preprocess failed: empty response body");
            }

            return body;

        } catch (RestClientException e) {
            log.error("NLP preprocess call failed", e);
            throw new NlpServiceException("NLP preprocess failed", e);
        }
    }

    public EmbedResponse embed(EmbedRequest request) {
        try {
            if (request == null) {
                throw new NlpServiceException("NLP embed failed: request is null");
            }
            HttpHeaders headers = buildHeaders(request.getCorrelationId());
            String resolvedCorrelationId = resolveCorrelationId(headers);

            HttpEntity<EmbedRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<EmbedResponse> resp = exchangeWithRetry(
                    baseUrl + "/embed",
                    entity,
                    EmbedResponse.class,
                    "embed",
                    resolvedCorrelationId
            );

            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new NlpServiceException(
                        "NLP embed failed: HTTP status " +
                                resp.getStatusCode().value()
                );
            }

            EmbedResponse body = resp.getBody();
            if (body == null) {
                throw new NlpServiceException("NLP embed failed: empty response body");
            }

            return body;

        } catch (RestClientException e) {
            log.error("NLP embed call failed", e);
            throw new NlpServiceException("NLP embed failed", e);
        }
    }
    public SentenceEmbedResponse embedSentences(SentenceEmbedRequest request) {
        try {
            if (request == null) {
                throw new NlpServiceException("NLP embed-sentences failed: request is null");
            }
            HttpHeaders headers = buildHeaders(request.getCorrelationId());
            String resolvedCorrelationId = resolveCorrelationId(headers);

            HttpEntity<SentenceEmbedRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<SentenceEmbedResponse> resp = exchangeWithRetry(
                    baseUrl + "/embed-sentences",
                    entity,
                    SentenceEmbedResponse.class,
                    "embed-sentences",
                    resolvedCorrelationId
            );

            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new NlpServiceException(
                        "NLP embed-sentences failed: HTTP status " +
                                resp.getStatusCode().value()
                );
            }

            SentenceEmbedResponse body = resp.getBody();
            if (body == null) {
                throw new NlpServiceException("NLP embed-sentences failed: empty response body");
            }

            return body;

        } catch (RestClientException e) {
            log.error("NLP embed-sentences call failed", e);
            throw new NlpServiceException("NLP embed-sentences failed", e);
        }
    }

    private HttpHeaders buildHeaders(String correlationId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String cid = (correlationId != null && !correlationId.isBlank())
                ? correlationId
                : UUID.randomUUID().toString();
        headers.set("X-Correlation-Id", cid);

        if (authTokenProvider != null && authTokenProvider.isEnabled()) {
            headers.setBearerAuth(authTokenProvider.getIdTokenValue());
        }

        return headers;
    }

    private static String resolveCorrelationId(HttpHeaders headers) {
        if (headers == null) {
            return "-";
        }
        String cid = headers.getFirst("X-Correlation-Id");
        return (cid == null || cid.isBlank()) ? "-" : cid;
    }

    private <T> ResponseEntity<T> exchangeWithRetry(
            String url,
            HttpEntity<?> entity,
            Class<T> responseType,
            String operation,
            String correlationId
    ) {
        int attempts = Math.max(1, maxRetryAttempts);
        int attempt = 1;

        while (true) {
            try {
                ResponseEntity<T> resp = restTemplate.exchange(url, HttpMethod.POST, entity, responseType);
                if (isRetryableStatus(resp.getStatusCode().value()) && attempt < attempts) {
                    sleepBeforeRetry(operation, resp.getStatusCode().value(), attempt, attempts, correlationId);
                    attempt++;
                    continue;
                }
                return resp;
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if (isRetryableStatus(status) && attempt < attempts) {
                    sleepBeforeRetry(operation, status, attempt, attempts, correlationId);
                    attempt++;
                    continue;
                }
                throw e;
            }
        }
    }

    private boolean isRetryableStatus(int status) {
        return RETRY_STATUS_CODES.contains(status);
    }

    private void sleepBeforeRetry(
            String operation,
            int status,
            int attempt,
            int maxAttempts,
            String correlationId
    ) {
        long delay = computeBackoffMs(attempt);
        log.warn(
                "Retrying NLP {} after HTTP {} (attempt {}/{}) in {}ms correlationId={}",
                operation,
                status,
                attempt,
                maxAttempts,
                delay,
                correlationId
        );
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RestClientException("NLP retry interrupted", e);
        }
    }

    private long computeBackoffMs(int attempt) {
        long initial = Math.max(0L, retryInitialBackoffMs);
        long max = Math.max(initial, retryMaxBackoffMs);
        long multiplier = 1L << Math.max(0, attempt - 1);
        long delay = initial * multiplier;
        return Math.min(delay, max);
    }
}
