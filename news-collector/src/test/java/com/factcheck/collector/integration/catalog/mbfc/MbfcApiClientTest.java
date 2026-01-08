package com.factcheck.collector.integration.catalog.mbfc;

import com.factcheck.collector.exception.MbfcQuotaExceededException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class MbfcApiClientTest {

    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        objectMapper = new ObjectMapper();
        server = MockRestServiceServer.createServer(restTemplate);
    }

    @Test
    void fetchAll_parsesEntries() {
        String json = """
                [
                  {
                    "Source": "LiveLeak (ItemFix)",
                    "MBFC URL": "https://mediabiasfactcheck.com/liveleak/",
                    "Bias": "Questionable",
                    "Country": "United Kingdom",
                    "Factual Reporting": "Mixed",
                    "Media Type": "Website",
                    "Source URL": "itemfix.com",
                    "Credibility": "Low",
                    "Source ID#": 104914,
                    "": ""
                  }
                ]
                """;

        server.expect(requestTo("http://mbfc.test/fetch-data"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-RapidAPI-Key", "test-key"))
                .andExpect(header("X-RapidAPI-Host", "media-bias-fact-check-ratings-api2.p.rapidapi.com"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        MbfcApiClient client = new MbfcApiClient(restTemplate, objectMapper);
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "baseUrl", "http://mbfc.test");
        ReflectionTestUtils.setField(client, "rapidApiHost", "");

        List<MbfcApiEntry> entries = client.fetchAll();

        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().getSourceName()).isEqualTo("LiveLeak (ItemFix)");

        server.verify();
    }

    @Test
    void fetchAll_whenApiKeyMissing_throws() {
        MbfcApiClient client = new MbfcApiClient(restTemplate, objectMapper);
        ReflectionTestUtils.setField(client, "apiKey", "   ");

        assertThatThrownBy(client::fetchAll)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("key is missing");
    }

    @Test
    void fetchAll_whenBaseUrlEndsWithSlash_usesFetchDataWithoutDoubleSlash() {
        server.expect(requestTo("http://mbfc.test/fetch-data"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        MbfcApiClient client = new MbfcApiClient(restTemplate, objectMapper);
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "baseUrl", "http://mbfc.test/");

        assertThat(client.fetchAll()).isEmpty();
        server.verify();
    }

    @Test
    void fetchAll_whenNon2xx_throwsIncludesSnippet() {
        restTemplate.setErrorHandler(new org.springframework.web.client.NoOpResponseErrorHandler()); // let exchange() return 502 [web:272]

        String body = "x".repeat(600);

        server.expect(requestTo("http://mbfc.test/fetch-data"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body(body));

        MbfcApiClient client = new MbfcApiClient(restTemplate, objectMapper);
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "baseUrl", "http://mbfc.test");

        Throwable t = catchThrowable(client::fetchAll);

        assertThat(t)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MBFC parse failed");
        assertThat(t.getCause())
                .isInstanceOf(IllegalStateException.class);
        assertThat(t.getCause().getMessage())
                .contains("HTTP 502")
                .contains("body=")
                .contains("...");

        server.verify();
    }



    @Test
    void fetchAll_whenEmptyBody_throws() {
        server.expect(requestTo("http://mbfc.test/fetch-data"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("   ", MediaType.APPLICATION_JSON));

        MbfcApiClient client = new MbfcApiClient(restTemplate, objectMapper);
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "baseUrl", "http://mbfc.test");

        assertThatThrownBy(client::fetchAll)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("MBFC parse failed")
                .hasRootCauseMessage("MBFC fetch failed: empty response body");

        server.verify();
    }

    @Test
    void fetchAll_stripsUtf8BomBeforeParsing() {
        String jsonWithBom = "\uFEFF[]";

        server.expect(requestTo("http://mbfc.test/fetch-data"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonWithBom, MediaType.APPLICATION_JSON));

        MbfcApiClient client = new MbfcApiClient(restTemplate, objectMapper);
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "baseUrl", "http://mbfc.test");

        assertThat(client.fetchAll()).isEmpty();
        server.verify();
    }

    @Test
    void fetchAll_when429TooManyRequests_throwsQuotaExceeded() {
        server.expect(requestTo("http://mbfc.test/fetch-data"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("rate limit"));

        MbfcApiClient client = new MbfcApiClient(restTemplate, objectMapper);
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "baseUrl", "http://mbfc.test");

        assertThatThrownBy(client::fetchAll)
                .isInstanceOf(MbfcQuotaExceededException.class)
                .hasMessageContaining("quota exceeded");

        server.verify();
    }

    @Test
    void fetchAll_whenRestClientException_throwsIllegalStateWithCause() {
        server.expect(requestTo("http://mbfc.test/fetch-data"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(request -> {
                    throw new RestClientException("boom");
                });

        MbfcApiClient client = new MbfcApiClient(restTemplate, objectMapper);
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "baseUrl", "http://mbfc.test");

        assertThatThrownBy(client::fetchAll)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MBFC fetch failed")
                .hasCauseInstanceOf(RestClientException.class);

        server.verify();
    }

    @Test
    void fetchAll_whenJsonInvalid_throwsParseFailed() {
        server.expect(requestTo("http://mbfc.test/fetch-data"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{ not-json", MediaType.APPLICATION_JSON));

        MbfcApiClient client = new MbfcApiClient(restTemplate, objectMapper);
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "baseUrl", "http://mbfc.test");

        assertThatThrownBy(client::fetchAll)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MBFC parse failed");

        server.verify();
    }
}