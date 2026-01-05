package com.factcheck.collector.integration.newsapi;

import com.factcheck.collector.integration.newsapi.dto.NewsApiEverythingResponse;
import com.factcheck.collector.integration.newsapi.dto.NewsApiSourcesResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NewsApiClient {

    private final NewsApiProperties properties;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public NewsApiSourcesResponse fetchSources(String language) {
        ensureApiKey();
        URI uri = buildUri("/top-headlines/sources", Map.of(
                "apiKey", properties.getApiKey(),
                "language", language
        ));
        NewsApiSourcesResponse response = execute(uri, NewsApiSourcesResponse.class);
        validateStatus(response.status(), response.code(), response.message());
        return response;
    }

    public NewsApiEverythingResponse fetchEverything(String sources, String sortBy, int page, int pageSize) {
        ensureApiKey();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("apiKey", properties.getApiKey());
        params.put("sources", sources);
        params.put("sortBy", sortBy);
        params.put("page", String.valueOf(page));
        params.put("pageSize", String.valueOf(pageSize));

        URI uri = buildUri("/everything", params);
        NewsApiEverythingResponse response = execute(uri, NewsApiEverythingResponse.class);
        validateStatus(response.status(), response.code(), response.message());
        return response;
    }

    private void ensureApiKey() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("NEWSAPI_API_KEY is not configured");
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://newsapi.org/v2";
        }
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (trimmed.endsWith("/top-headlines") || trimmed.endsWith("/everything")) {
            return trimmed.substring(0, trimmed.lastIndexOf('/'));
        }
        return trimmed;
    }

    private URI buildUri(String path, Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        sb.append(normalizeBaseUrl(properties.getBaseUrl()));
        if (!path.startsWith("/")) {
            sb.append('/');
        }
        sb.append(path);
        if (!params.isEmpty()) {
            sb.append('?');
            boolean first = true;
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (!first) {
                    sb.append('&');
                }
                first = false;
                sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
                sb.append('=');
                sb.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            }
        }
        return URI.create(sb.toString());
    }

    private <T> T execute(URI uri, Class<T> responseType) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("NewsAPI HTTP " + response.statusCode() + " for " + uri);
            }
            return objectMapper.readValue(response.body(), responseType);
        } catch (Exception e) {
            throw new IllegalStateException("NewsAPI request failed for " + uri, e);
        }
    }

    private void validateStatus(String status, String code, String message) {
        if (!"ok".equalsIgnoreCase(status)) {
            String detail = message != null ? message : "Unknown error";
            String errCode = code != null ? code : "unknown";
            throw new IllegalStateException("NewsAPI error [" + errCode + "]: " + detail);
        }
    }
}
