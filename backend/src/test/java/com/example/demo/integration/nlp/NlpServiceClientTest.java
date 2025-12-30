package com.example.demo.integration.nlp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NlpServiceClientTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private NlpServiceClient client;

    @BeforeEach
    void setup() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new NlpServiceClient(restTemplate);
        ReflectionTestUtils.setField(client, "baseUrl", "http://localhost");
    }

    @Test
    void embed_returnsEmbeddingsOnSuccess() {
        String body = """
                {
                  "embeddings": [[0.1, 0.2, 0.3]],
                  "dimension": 3,
                  "model": "demo",
                  "processingTimeMs": 12,
                  "correlationId": "cid-123"
                }
                """;

        server.expect(requestTo("http://localhost/embed"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("X-Correlation-Id", "cid-123"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        EmbedResponse response = client.embed(List.of("hello"), "cid-123");

        assertThat(response.getEmbeddings()).hasSize(1);
        assertThat(response.getEmbeddings().getFirst()).containsExactly(0.1, 0.2, 0.3);
        assertThat(response.getDimension()).isEqualTo(3);
        server.verify();
    }

    @Test
    void embed_throwsWhenBodyMissing() {
        server.expect(requestTo("http://localhost/embed"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.embed(List.of("hello"), "cid-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("empty response body");
    }

    @Test
    void embed_wrapsServerErrors() {
        server.expect(requestTo("http://localhost/embed"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.embed(List.of("boom"), null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("NLP embed failed");
    }

    @Test
    void embedSingleToVectorThrowsWhenNoEmbeddings() {
        NlpServiceClient spy = Mockito.spy(client);

        EmbedResponse empty = new EmbedResponse();
        empty.setEmbeddings(List.of());

        doReturn(empty).when(spy).embed(List.of("text"), "cid");

        assertThatThrownBy(() -> spy.embedSingleToVector("text", "cid"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no embeddings");
    }

    @Test
    void embedSingleToVectorThrowsWhenFirstVectorEmpty() {
        EmbedResponse response = new EmbedResponse();
        response.setEmbeddings(List.of(List.of()));

        NlpServiceClient stubClient = new NlpServiceClient(restTemplate) {
            @Override
            public EmbedResponse embed(List<String> texts, String correlationId) {
                return response;
            }
        };

        assertThatThrownBy(() -> stubClient.embedSingleToVector("text", "cid"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("First embedding vector is empty");
    }

    @Test
    void embedSingleToVectorConvertsNullValuesToZero() {
        EmbedResponse response = new EmbedResponse();
        response.setEmbeddings(List.of(Arrays.asList(1.0, null, 3.0)));
        response.setDimension(3);

        NlpServiceClient stubClient = new NlpServiceClient(restTemplate) {
            @Override
            public EmbedResponse embed(List<String> texts, String correlationId) {
                return response;
            }
        };

        float[] vector = stubClient.embedSingleToVector("text", "cid");
        assertThat(vector).containsExactly(1.0f, 0.0f, 3.0f);
    }
}
