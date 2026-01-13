package com.factcheck.collector.service.processing;

import com.factcheck.collector.domain.entity.Article;
import com.factcheck.collector.dto.ChunkingResult;
import com.factcheck.collector.integration.nlp.NlpServiceClient;
import com.factcheck.collector.integration.nlp.dto.PreprocessResponse;
import com.factcheck.collector.integration.nlp.dto.SentenceEmbedResponse;
import com.factcheck.collector.util.SemanticBoundaryDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleProcessingServiceTest {

    @Mock
    private NlpServiceClient nlpClient;

    @Mock
    private SemanticBoundaryDetector boundaryDetector;

    @Test
    void createChunksNonSemantic_chunksSentencesAndReturnsNoEmbeddings() {
        ArticleProcessingService service = new ArticleProcessingService(nlpClient, boundaryDetector);

        ReflectionTestUtils.setField(service, "useSemanticChunking", false);

        Article article = Article.builder().id(11L).build();

        PreprocessResponse preprocessResponse = new PreprocessResponse();
        preprocessResponse.setSentences(List.of(
                "Sentence one",
                "Sentence two",
                "Sentence three",
                "Sentence four",
                "Sentence five"
        ));
        when(nlpClient.preprocess("full text", "cid-123")).thenReturn(preprocessResponse);

        ChunkingResult result = service.createChunks(article, "full text", "cid-123");

        verify(nlpClient).preprocess("full text", "cid-123");

        assertThat(result.semanticUsed()).isFalse();
        assertThat(result.embeddings()).isNull();
        assertThat(result.chunks())
                .hasSize(2)
                .containsExactly(
                        "Sentence one Sentence two Sentence three Sentence four",
                        "Sentence five"
                );
    }

    @Test
    void createChunksSemantic_returnsPrecomputedChunkEmbeddings() {
        ArticleProcessingService service = new ArticleProcessingService(nlpClient, boundaryDetector);

        ReflectionTestUtils.setField(service, "useSemanticChunking", true);
        ReflectionTestUtils.setField(service, "semanticMinSentences", 1);
        ReflectionTestUtils.setField(service, "minSentences", 1);
        ReflectionTestUtils.setField(service, "maxSentences", 100);
        ReflectionTestUtils.setField(service, "maxTokens", 10000);
        ReflectionTestUtils.setField(service, "overlapSentences", 0);
        ReflectionTestUtils.setField(service, "similarityThreshold", 0.65d);

        Article article = Article.builder().id(11L).build();

        PreprocessResponse preprocessResponse = new PreprocessResponse();
        preprocessResponse.setSentences(List.of("S1", "S2", "S3"));
        when(nlpClient.preprocess("full text", "cid-123")).thenReturn(preprocessResponse);

        SentenceEmbedResponse embedResp = new SentenceEmbedResponse();
        embedResp.setEmbeddings(List.of(
                List.of(1.0d, 0.0d),
                List.of(0.0d, 1.0d),
                List.of(1.0d, 1.0d)
        ));
        when(nlpClient.embedSentences(any())).thenReturn(embedResp);

        // split after sentence index 2 (i == 2)
        when(boundaryDetector.detectBoundaries(eq(preprocessResponse.getSentences()), anyList(), anyDouble()))
                .thenReturn(List.of(2));

        ChunkingResult result = service.createChunks(article, "full text", "cid-123");

        assertThat(result.semanticUsed()).isTrue();
        assertThat(result.chunks()).hasSize(2);
        assertThat(result.embeddings()).isNotNull();
        assertThat(result.embeddings()).hasSize(2);

        verify(nlpClient).preprocess("full text", "cid-123");
        verify(nlpClient).embedSentences(any());
        verify(boundaryDetector).detectBoundaries(eq(preprocessResponse.getSentences()), anyList(), anyDouble());
    }
}