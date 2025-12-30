package com.example.demo.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VertexEmbeddingServiceTest {

    @Mock
    VertexAuthHelper authHelper;

    @Mock
    VertexApiClient vertexApiClient;

    @InjectMocks
    VertexEmbeddingService embeddingService;

    @Test
    void embedText_parsesEmbeddingVectorFromResponse() throws Exception {
        when(authHelper.embeddingEndpoint()).thenReturn("https://example.com/embed");

        String json = """
                {
                  "predictions": [
                    {
                      "embeddings": {
                        "values": [0.1, 0.2, 0.3]
                      }
                    }
                  ]
                }
                """;

        HttpResponse<String> httpResp = mock(HttpResponse.class);
        when(httpResp.statusCode()).thenReturn(200);
        when(httpResp.body()).thenReturn(json);

        when(vertexApiClient.postJson(anyString(), anyString()))
                .thenReturn(httpResp);

        float[] vector = embeddingService.embedText("text");

        assertThat(vector).containsExactly(0.1f, 0.2f, 0.3f);
    }

    @Test
    void embedText_throwsOnNon2xxStatus() throws Exception {
        when(authHelper.embeddingEndpoint()).thenReturn("https://example.com/embed");

        HttpResponse<String> httpResp = mock(HttpResponse.class);
        when(httpResp.statusCode()).thenReturn(500);
        when(httpResp.body()).thenReturn("error");

        when(vertexApiClient.postJson(anyString(), anyString()))
                .thenReturn(httpResp);

        assertThatThrownBy(() -> embeddingService.embedText("text"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Embedding error 500");
    }

    @Test
    void embedText_throwsWhenEmbeddingsMissing() throws Exception {
        when(authHelper.embeddingEndpoint()).thenReturn("https://example.com/embed");

        String json = """
                {
                  "predictions": [
                    {
                      "embeddings": {
                        "values": []
                      }
                    }
                  ]
                }
                """;

        HttpResponse<String> httpResp = mock(HttpResponse.class);
        when(httpResp.statusCode()).thenReturn(200);
        when(httpResp.body()).thenReturn(json);

        when(vertexApiClient.postJson(anyString(), anyString()))
                .thenReturn(httpResp);

        assertThatThrownBy(() -> embeddingService.embedText("text"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No embeddings.values field");
    }

    @Test
    void embedText_throwsWhenPredictionsMissing() throws Exception {
        when(authHelper.embeddingEndpoint()).thenReturn("https://example.com/embed");

        String json = """
                {
                  "predictions": []
                }
                """;

        HttpResponse<String> httpResp = mock(HttpResponse.class);
        when(httpResp.statusCode()).thenReturn(200);
        when(httpResp.body()).thenReturn(json);
        when(vertexApiClient.postJson(anyString(), anyString())).thenReturn(httpResp);

        assertThatThrownBy(() -> embeddingService.embedText("text"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No predictions field in embedding response");
    }
}
