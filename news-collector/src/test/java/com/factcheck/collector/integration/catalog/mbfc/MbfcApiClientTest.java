package com.factcheck.collector.integration.catalog.mbfc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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
                  },
                  {
                    "Source": "Montreal Gazette",
                    "MBFC URL": "https://mediabiasfactcheck.com/montreal-gazette/",
                    "Bias": "Right-Center",
                    "Country": "Canada",
                    "Factual Reporting": "High",
                    "Media Type": "Newspaper",
                    "Source URL": "montrealgazette.com",
                    "Credibility": "High",
                    "Source ID#": 107142,
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

        List<MbfcApiEntry> entries = client.fetchAll();

        assertThat(entries).hasSize(2);
        MbfcApiEntry first = entries.getFirst();
        assertThat(first.getSourceName()).isEqualTo("LiveLeak (ItemFix)");
        assertThat(first.getMbfcUrl()).isEqualTo("https://mediabiasfactcheck.com/liveleak/");
        assertThat(first.getSourceId()).isEqualTo(104914L);

        server.verify();
    }
}