package com.factcheck.collector.service.processing;

import com.factcheck.collector.integration.nlp.NlpServiceClient;
import com.factcheck.collector.integration.nlp.dto.EmbedRequest;
import com.factcheck.collector.integration.nlp.dto.EmbedResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    @Mock
    private NlpServiceClient nlpServiceClient;

    @InjectMocks
    private EmbeddingService embeddingService;

    @Test
    void embedChunksSendsCorrelationIdAndReturnsEmbeddings() {
        List<String> chunks = List.of("first", "second");

        EmbedResponse embedResponse = new EmbedResponse();
        embedResponse.setEmbeddings(List.of(
                List.of(0.1d, 0.2d),
                List.of(0.3d, 0.4d)
        ));

        when(nlpServiceClient.embed(any(EmbedRequest.class)))
                .thenReturn(embedResponse);

        List<List<Double>> embeddings = embeddingService.embedChunks(chunks, "corr-123");

        ArgumentCaptor<EmbedRequest> requestCaptor = ArgumentCaptor.forClass(EmbedRequest.class);
        verify(nlpServiceClient).embed(requestCaptor.capture());

        EmbedRequest request = requestCaptor.getValue();
        assertThat(request.getTexts()).containsExactlyElementsOf(chunks);
        assertThat(request.getCorrelationId()).isEqualTo("corr-123");
        assertThat(embeddings).containsExactlyElementsOf(embedResponse.getEmbeddings());
    }

    @Test
    void embedChunksRejectsTooManyChunks() {
        List<String> chunks = java.util.stream.IntStream.range(0, 101)
                .mapToObj(i -> "chunk-" + i)
                .toList();

        assertThatThrownBy(() -> embeddingService.embedChunks(chunks, "corr-456"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Too many chunks for embedding");

        verify(nlpServiceClient, never()).embed(any(EmbedRequest.class));
    }
}