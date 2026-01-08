package com.factcheck.collector.integration.catalog.mbfc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.factcheck.collector.exception.MbfcQuotaExceededException;

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

    @Value("${mbfc.rapidapi.host:" + DEFAULT_HOST + "}")
    private String rapidApiHost = DEFAULT_HOST;

    public List<MbfcApiEntry> fetchAll() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("MBFC RapidAPI key is missing (mbfc.rapidapi.key or RAPIDAPI_KEY)");
        }

        String url = baseUrl.endsWith("/") ? (baseUrl + "fetch-data") : (baseUrl + "/fetch-data");

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-RapidAPI-Key", apiKey);
            String host = (rapidApiHost == null || rapidApiHost.isBlank()) ? DEFAULT_HOST : rapidApiHost;
            headers.set("X-RapidAPI-Host", host);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> resp = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            if (!resp.getStatusCode().is2xxSuccessful()) {
                String bodySnippet = safeSnippet(resp.getBody(), 500);
                throw new IllegalStateException(
                        "MBFC fetch failed: HTTP " + resp.getStatusCode().value() + " body=" + bodySnippet
                );
            }

            String body = resp.getBody();
            if (body == null || body.isBlank()) {
                throw new IllegalStateException("MBFC fetch failed: empty response body");
            }

            String normalizedBody = stripBom(body);
            return objectMapper.readValue(normalizedBody, new TypeReference<List<MbfcApiEntry>>() {});
        } catch (HttpClientErrorException.TooManyRequests e) {
            String msg = "MBFC quota exceeded; skipping sync";
            log.warn(msg);
            throw new MbfcQuotaExceededException(msg, e);
        } catch (RestClientException e) {
            log.error("MBFC fetch failed (HTTP client error)", e);
            throw new IllegalStateException("MBFC fetch failed", e);
        } catch (Exception e) {
            log.error("MBFC parse failed", e);
            throw new IllegalStateException("MBFC parse failed", e);
        }
    }

    private static String stripBom(String body) {
        if (body == null || body.isEmpty()) return body;
        return body.charAt(0) == '\uFEFF' ? body.substring(1) : body;
    }

    private static String safeSnippet(String s, int max) {
        if (s == null) return "null";
        String trimmed = s.trim();
        if (trimmed.length() <= max) return trimmed;
        return trimmed.substring(0, max) + "...";
    }
}