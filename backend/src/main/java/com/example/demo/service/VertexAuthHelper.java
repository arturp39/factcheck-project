package com.example.demo.service;

import com.example.demo.config.VertexProperties;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class VertexAuthHelper {

    private static final String SCOPE = "https://www.googleapis.com/auth/cloud-platform";

    private final VertexProperties props;

    public String fetchAccessToken() throws IOException {
        GoogleCredentials credentials;
        String credentialsPath = props.getCredentialsPath();

        if (credentialsPath != null && !credentialsPath.isBlank()) {
            log.debug("Using Vertex credentials from path={}", credentialsPath);
            try (FileInputStream fis = new FileInputStream(credentialsPath)) {
                credentials = GoogleCredentials.fromStream(fis);
            }
        } else {
            log.debug("Using Application Default Credentials for Vertex");
            credentials = GoogleCredentials.getApplicationDefault();
        }

        credentials = credentials.createScoped(Collections.singleton(SCOPE));
        credentials.refreshIfExpired();
        return credentials.getAccessToken().getTokenValue();
    }

    public String embeddingEndpoint() {
        String endpoint = String.format(
                "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/publishers/google/models/%s:predict",
                props.getLocation(),
                props.getProjectId(),
                props.getLocation(),
                "text-embedding-004"
        );
        log.debug("Embedding endpoint={}", endpoint);
        return endpoint;
    }

    public String chatEndpoint() {
        String endpoint = String.format(
                "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/publishers/google/models/%s:generateContent",
                props.getLocation(),
                props.getProjectId(),
                props.getLocation(),
                props.getModelName()
        );
        log.debug("Chat endpoint={}", endpoint);
        return endpoint;
    }
}