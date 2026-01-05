package com.factcheck.collector.integration.mbfc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MbfcApiClient {

    private static final String DEFAULT_HOST = "media-bias-fact-check-ratings-api2.p.rapidapi.com";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${mbfc.rapidapi.key:${RAPIDAPI_KEY:}}")
    private String apiKey;

    @Value("${mbfc.rapidapi.base-url:https://media-bias-fact-check-ratings-api2.p.rapidapi.com}")
    private String baseUrl;

    public List<MbfcApiEntry> fetchAll() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("MBFC RapidAPI key is missing (mbfc.rapidapi.key or RAPIDAPI_KEY)");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-RapidAPI-Key", apiKey);
            headers.set("X-RapidAPI-Host", DEFAULT_HOST);

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl + "/fetch-data",
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            if (resp == null || !resp.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException(
                        "MBFC fetch failed: HTTP status " + (resp != null ? resp.getStatusCode() : "null response")
                );
            }

            String body = resp.getBody();
            if (body == null || body.isBlank()) {
                throw new IllegalStateException("MBFC fetch failed: empty response body");
            }

            return objectMapper.readValue(body, new TypeReference<List<MbfcApiEntry>>() {});
        } catch (RestClientException e) {
            log.error("MBFC fetch failed", e);
            throw new IllegalStateException("MBFC fetch failed", e);
        } catch (Exception e) {
            log.error("MBFC parse failed", e);
            throw new IllegalStateException("MBFC parse failed", e);
        }
    }
}
