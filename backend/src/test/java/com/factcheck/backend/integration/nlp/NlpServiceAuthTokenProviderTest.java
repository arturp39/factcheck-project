package com.factcheck.backend.integration.nlp;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.IdTokenCredentials;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NlpServiceAuthTokenProviderTest {

    @Test
    void constructor_throwsWhenEnabledWithoutAudienceOrBaseUrl() {
        assertThatThrownBy(() -> new NlpServiceAuthTokenProvider(true, "   ", "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nlp-service.auth.audience is required");
    }

    @Test
    void constructor_usesBaseUrlWhenAudienceBlank() {
        NlpServiceAuthTokenProvider provider =
                new NlpServiceAuthTokenProvider(true, " ", " https://nlp.example ");

        String targetAudience = (String) ReflectionTestUtils.getField(provider, "targetAudience");

        assertThat(targetAudience).isEqualTo("https://nlp.example");
    }

    @Test
    void getIdTokenValue_returnsNullWhenDisabled() {
        NlpServiceAuthTokenProvider provider =
                new NlpServiceAuthTokenProvider(false, "https://aud", "https://nlp");

        assertThat(provider.getIdTokenValue()).isNull();
    }

    @Test
    void getIdTokenValue_returnsTokenWhenAvailable() throws Exception {
        NlpServiceAuthTokenProvider provider =
                new NlpServiceAuthTokenProvider(true, "https://aud", "https://nlp");
        IdTokenCredentials creds = mock(IdTokenCredentials.class);
        doNothing().when(creds).refreshIfExpired();
        when(creds.getAccessToken()).thenReturn(new AccessToken("token", new Date()));
        ReflectionTestUtils.setField(provider, "credentials", creds);

        String token = provider.getIdTokenValue();

        assertThat(token).isEqualTo("token");
    }

    @Test
    void getIdTokenValue_throwsWhenTokenMissing() throws Exception {
        NlpServiceAuthTokenProvider provider =
                new NlpServiceAuthTokenProvider(true, "https://aud", "https://nlp");
        IdTokenCredentials creds = mock(IdTokenCredentials.class);
        doNothing().when(creds).refreshIfExpired();
        when(creds.getAccessToken()).thenReturn(null);
        ReflectionTestUtils.setField(provider, "credentials", creds);

        assertThatThrownBy(provider::getIdTokenValue)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ID token is empty");
    }

    @Test
    void getIdTokenValue_throwsWhenTokenBlank() throws Exception {
        NlpServiceAuthTokenProvider provider =
                new NlpServiceAuthTokenProvider(true, "https://aud", "https://nlp");
        IdTokenCredentials creds = mock(IdTokenCredentials.class);
        doNothing().when(creds).refreshIfExpired();
        when(creds.getAccessToken()).thenReturn(new AccessToken("   ", new Date()));
        ReflectionTestUtils.setField(provider, "credentials", creds);

        assertThatThrownBy(provider::getIdTokenValue)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ID token is empty");
    }

    @Test
    void getIdTokenValue_throwsWhenTokenValueNull() throws Exception {
        NlpServiceAuthTokenProvider provider =
                new NlpServiceAuthTokenProvider(true, "https://aud", "https://nlp");
        IdTokenCredentials creds = mock(IdTokenCredentials.class);
        AccessToken token = mock(AccessToken.class);
        doNothing().when(creds).refreshIfExpired();
        when(token.getTokenValue()).thenReturn(null);
        when(creds.getAccessToken()).thenReturn(token);
        ReflectionTestUtils.setField(provider, "credentials", creds);

        assertThatThrownBy(provider::getIdTokenValue)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ID token is empty");
    }

    @Test
    void getIdTokenValue_wrapsIOException() throws Exception {
        NlpServiceAuthTokenProvider provider =
                new NlpServiceAuthTokenProvider(true, "https://aud", "https://nlp");
        IdTokenCredentials creds = mock(IdTokenCredentials.class);
        doThrow(new IOException("boom")).when(creds).refreshIfExpired();
        ReflectionTestUtils.setField(provider, "credentials", creds);

        assertThatThrownBy(provider::getIdTokenValue)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to fetch ID token");
    }
}
