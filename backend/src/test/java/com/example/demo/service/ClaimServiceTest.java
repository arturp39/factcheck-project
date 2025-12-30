package com.example.demo.service;

import com.example.demo.entity.Article;
import com.example.demo.entity.ClaimLog;
import com.example.demo.integration.nlp.NlpServiceClient;
import com.example.demo.repository.ClaimLogRepository;
import com.example.demo.service.WeaviateClientService.EvidenceChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {

    @Mock
    private ClaimLogRepository claimLogRepository;

    @Mock
    private NlpServiceClient nlpServiceClient;

    @Mock
    private WeaviateClientService weaviateClientService;

    private ClaimService claimService;

    private ClaimLog existingLog;

    @BeforeEach
    void setUp() {
        existingLog = new ClaimLog();
        existingLog.setId(1L);
        existingLog.setClaimText("The Earth is flat");

        claimService = new ClaimService(claimLogRepository, nlpServiceClient, weaviateClientService, 5);
    }

    @Test
    void searchEvidence_mapsWeaviateChunksToArticles() throws Exception {
        String claim = "The Earth is flat";

        when(nlpServiceClient.embedSingleToVector(eq(claim), anyString()))
                .thenReturn(new float[]{0.1f, 0.2f, 0.3f});

        when(weaviateClientService.searchByVector(any(float[].class), eq(5)))
                .thenReturn("{}");

        List<EvidenceChunk> chunks = List.of(
                new EvidenceChunk("Title 1", "Content 1", "Source 1"),
                new EvidenceChunk("Title 2", "Content 2", "Source 2")
        );
        when(weaviateClientService.parseEvidenceChunks(anyString()))
                .thenReturn(chunks);

        List<Article> articles = claimService.searchEvidence(claim);

        assertThat(articles).hasSize(2);
        assertThat(articles.get(0).getTitle()).isEqualTo("Title 1");
        assertThat(articles.get(0).getContent()).isEqualTo("Content 1");
        assertThat(articles.get(0).getSource()).isEqualTo("Source 1");
        assertThat(articles.get(1).getTitle()).isEqualTo("Title 2");
    }

    @Test
    void searchEvidence_wrapsExceptionsIntoRuntime() throws Exception {
        when(nlpServiceClient.embedSingleToVector(anyString(), anyString()))
                .thenThrow(new RuntimeException("NLP down"));

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> claimService.searchEvidence("some claim")
        );

        assertThat(ex.getMessage()).contains("Vector search failed");
    }

    @Test
    void saveClaim_persistsAndReturnsEntity() {
        String text = "Vaccines cause autism";

        ClaimLog toSave = new ClaimLog();
        toSave.setId(10L);
        toSave.setClaimText(text);

        when(claimLogRepository.save(any(ClaimLog.class)))
                .thenReturn(toSave);

        ClaimLog saved = claimService.saveClaim(text);

        assertThat(saved.getId()).isEqualTo(10L);
        assertThat(saved.getClaimText()).isEqualTo(text);

        ArgumentCaptor<ClaimLog> captor = ArgumentCaptor.forClass(ClaimLog.class);
        verify(claimLogRepository).save(captor.capture());
        assertThat(captor.getValue().getClaimText()).isEqualTo(text);
    }

    @Test
    void storeModelAnswer_parsesVerdictExplanationAndSaves() {
        String rawAnswer = """
                Verdict: mixed
                Explanation: Some studies support this, others contradict it.
                """;

        when(claimLogRepository.findById(1L)).thenReturn(Optional.of(existingLog));

        ClaimService.ParsedAnswer parsed =
                claimService.storeModelAnswer(1L, rawAnswer);

        assertThat(parsed.verdict()).isEqualTo("mixed");
        assertThat(parsed.explanation())
                .contains("Some studies support this");

        ArgumentCaptor<ClaimLog> captor = ArgumentCaptor.forClass(ClaimLog.class);
        verify(claimLogRepository).save(captor.capture());

        ClaimLog saved = captor.getValue();
        assertThat(saved.getVerdict()).isEqualTo("mixed");
        assertThat(saved.getExplanation()).contains("Some studies support this");
        assertThat(saved.getModelAnswer()).isEqualTo(rawAnswer);
    }

    @Test
    void storeModelAnswerHandlesNullAnswer() {
        when(claimLogRepository.findById(1L)).thenReturn(Optional.of(existingLog));

        ClaimService.ParsedAnswer parsed = claimService.storeModelAnswer(1L, null);

        assertThat(parsed.verdict()).isEqualTo("unclear");
        assertThat(parsed.explanation()).contains("(no explanation)");
    }

    @Test
    void storeModelAnswerDefaultsExplanationWhenBlank() {
        String rawAnswer = "Verdict: false";
        when(claimLogRepository.findById(1L)).thenReturn(Optional.of(existingLog));

        ClaimService.ParsedAnswer parsed = claimService.storeModelAnswer(1L, rawAnswer);

        assertThat(parsed.verdict()).isEqualTo("false");
        assertThat(parsed.explanation()).isEqualTo("(no explanation)");
    }

    @Test
    void storeBiasAnalysis_updatesClaimLog() {
        when(claimLogRepository.findById(1L)).thenReturn(Optional.of(existingLog));

        String biasText = "Sources are heavily skewed towards UK media.";
        claimService.storeBiasAnalysis(1L, biasText);

        ArgumentCaptor<ClaimLog> captor = ArgumentCaptor.forClass(ClaimLog.class);
        verify(claimLogRepository).save(captor.capture());

        ClaimLog saved = captor.getValue();
        assertThat(saved.getBiasAnalysis()).isEqualTo(biasText);
    }

    @Test
    void getClaim_throwsIfNotFound() {
        when(claimLogRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> claimService.getClaim(99L));
    }
}
