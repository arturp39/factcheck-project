package com.example.demo.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Slf4j
@Component
@RequiredArgsConstructor
public class VertexApiClient {

    private final VertexAuthHelper authHelper;
    private final HttpClient client = HttpClient.newHttpClient();

    public HttpResponse<String> postJson(String endpoint, String jsonBody) throws Exception {
        String token = authHelper.fetchAccessToken();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        log.debug("Calling Vertex endpoint={} bodyLength={}", endpoint, jsonBody.length());
        HttpResponse<String> resp =
                client.send(request, HttpResponse.BodyHandlers.ofString());
        log.debug("Vertex response status={} body={}", resp.statusCode(), resp.body());
        return resp;
    }
}