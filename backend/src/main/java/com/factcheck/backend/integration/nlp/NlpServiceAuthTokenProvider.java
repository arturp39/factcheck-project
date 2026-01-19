package com.factcheck.backend.integration.nlp;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class NlpServiceAuthTokenProvider {

    @Getter
    private final boolean enabled;
    private final String targetAudience;
    private volatile IdTokenCredentials credentials;

    public NlpServiceAuthTokenProvider(
            @Value("${nlp-service.auth.enabled:false}") boolean enabled,
            @Value("${nlp-service.auth.audience:}") String audience,
            @Value("${nlp-service.url:}") String baseUrl
    ) {
        this.enabled = enabled;
        String resolved = (audience != null && !audience.isBlank()) ? audience : baseUrl;
        this.targetAudience = resolved != null ? resolved.trim() : null;
        if (this.enabled && (this.targetAudience == null || this.targetAudience.isBlank())) {
            throw new IllegalArgumentException("nlp-service.auth.audience is required when auth is enabled");
        }
    }

    public String getIdTokenValue() {
        if (!enabled) {
            return null;
        }
        try {
            IdTokenCredentials creds = getCredentials();
            creds.refreshIfExpired();
            AccessToken token = creds.getAccessToken();
            if (token == null || token.getTokenValue() == null || token.getTokenValue().isBlank()) {
                throw new IllegalStateException("ID token is empty");
            }
            return token.getTokenValue();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to fetch ID token for NLP service", e);
        }
    }

    private IdTokenCredentials getCredentials() throws IOException {
        if (credentials != null) {
            return credentials;
        }
        synchronized (this) {
            if (credentials == null) {
                GoogleCredentials base = GoogleCredentials.getApplicationDefault();
                if (!(base instanceof IdTokenProvider)) {
                    throw new IllegalStateException(
                            "Default credentials do not support ID tokens; check service account configuration."
                    );
                }
                credentials = IdTokenCredentials.newBuilder()
                        .setIdTokenProvider((IdTokenProvider) base)
                        .setTargetAudience(targetAudience)
                        .build();
                log.info("NLP service auth enabled with targetAudience={}", targetAudience);
            }
        }
        return credentials;
    }
}
