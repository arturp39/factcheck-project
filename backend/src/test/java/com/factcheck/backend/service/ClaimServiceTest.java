package com.factcheck.backend.service;

import com.factcheck.backend.dto.ArticleDto;
import com.factcheck.backend.entity.ClaimFollowup;
import com.factcheck.backend.entity.ClaimLog;
import com.factcheck.backend.integration.nlp.NlpServiceClient;
import com.factcheck.backend.repository.ClaimFollowupRepository;
import com.factcheck.backend.repository.ClaimLogRepository;
import com.factcheck.backend.service.WeaviateClientService.EvidenceChunk;
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
    private ClaimFollowupRepository claimFollowupRepository;

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
        existingLog.setOwnerUsername("user1");

        claimService = new ClaimService(
                claimLogRepository,
                claimFollowupRepository,
                nlpServiceClient,
                weaviateClientService,
                5
        );
    }

    @Test
    void searchEvidence_mapsWeaviateChunksToArticles() throws Exception {
        String claim = "The Earth is flat";

        when(nlpServiceClient.embedSingleToVector(eq(claim), anyString()))
                .thenReturn(new float[]{0.1f, 0.2f, 0.3f});

        when(weaviateClientService.searchByVector(any(float[].class), eq(5), anyString()))
                .thenReturn("{}");

        List<EvidenceChunk> chunks = List.of(
                new EvidenceChunk(11L, "Title 1", "Content 1", "Source 1", null, "https://example.com/a1", null, null, null),
                new EvidenceChunk(12L, "Title 2", "Content 2", "Source 2", null, "https://example.com/a2", null, null, null)
        );
        when(weaviateClientService.parseEvidenceChunks(anyString()))
                .thenReturn(chunks);

        List<ArticleDto> articles = claimService.searchEvidence(claim);

        assertThat(articles).hasSize(2);
        assertThat(articles.get(0).title()).isEqualTo("Title 1");
        assertThat(articles.get(0).content()).isEqualTo("Content 1");
        assertThat(articles.get(0).source()).isEqualTo("Source 1");
        assertThat(articles.get(0).articleId()).isEqualTo(11L);
        assertThat(articles.get(0).url()).isEqualTo("https://example.com/a1");
        assertThat(articles.get(1).title()).isEqualTo("Title 2");
    }

    @Test
    void searchEvidence_filtersBadSources() throws Exception {
        String claim = "The Earth is flat";

        when(nlpServiceClient.embedSingleToVector(eq(claim), anyString()))
                .thenReturn(new float[]{0.1f, 0.2f, 0.3f});

        when(weaviateClientService.searchByVector(any(float[].class), eq(5), anyString()))
                .thenReturn("{}");

        List<EvidenceChunk> chunks = List.of(
                new EvidenceChunk(11L, "Good", "Content 1", "Source 1", null, "https://example.com/a1", "center", "high", "high"),
                new EvidenceChunk(12L, "Bad", "Content 2", "Source 2", null, "https://example.com/a2", "questionable", "very low", "low")
        );
        when(weaviateClientService.parseEvidenceChunks(anyString()))
                .thenReturn(chunks);

        List<ArticleDto> articles = claimService.searchEvidence(claim);

        assertThat(articles).hasSize(1);
        assertThat(articles.get(0).title()).isEqualTo("Good");
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
        toSave.setOwnerUsername("user1");

        when(claimLogRepository.save(any(ClaimLog.class)))
                .thenReturn(toSave);

        ClaimLog saved = claimService.saveClaim(text, "user1");

        assertThat(saved.getId()).isEqualTo(10L);
        assertThat(saved.getClaimText()).isEqualTo(text);

        ArgumentCaptor<ClaimLog> captor = ArgumentCaptor.forClass(ClaimLog.class);
        verify(claimLogRepository).save(captor.capture());
        assertThat(captor.getValue().getClaimText()).isEqualTo(text);
        assertThat(captor.getValue().getOwnerUsername()).isEqualTo("user1");
    }

    @Test
    void storeModelAnswer_parsesVerdictExplanationAndSaves() {
        String rawAnswer = """
                Verdict: mixed
                Explanation: Some studies support this, others contradict it.
                """;

        when(claimLogRepository.findById(1L)).thenReturn(Optional.of(existingLog));

        ClaimService.ParsedAnswer parsed =
                claimService.storeModelAnswer(1L, rawAnswer, "user1", false);

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

        ClaimService.ParsedAnswer parsed = claimService.storeModelAnswer(1L, null, "user1", false);

        assertThat(parsed.verdict()).isEqualTo("unclear");
        assertThat(parsed.explanation()).contains("(no explanation)");
    }

    @Test
    void storeModelAnswerDefaultsExplanationWhenBlank() {
        String rawAnswer = "Verdict: false";
        when(claimLogRepository.findById(1L)).thenReturn(Optional.of(existingLog));

        ClaimService.ParsedAnswer parsed = claimService.storeModelAnswer(1L, rawAnswer, "user1", false);

        assertThat(parsed.verdict()).isEqualTo("false");
        assertThat(parsed.explanation()).isEqualTo("(no explanation)");
    }

    @Test
    void storeBiasAnalysis_updatesClaimLog() {
        when(claimLogRepository.findById(1L)).thenReturn(Optional.of(existingLog));

        String biasText = "Sources are heavily skewed towards UK media.";
        claimService.storeBiasAnalysis(1L, biasText, "user1", false);

        ArgumentCaptor<ClaimLog> captor = ArgumentCaptor.forClass(ClaimLog.class);
        verify(claimLogRepository).save(captor.capture());

        ClaimLog saved = captor.getValue();
        assertThat(saved.getBiasAnalysis()).isEqualTo(biasText);
    }

    @Test
    void storeFollowup_persistsQuestionAndAnswer() {
        when(claimLogRepository.findById(1L)).thenReturn(Optional.of(existingLog));
        ClaimFollowup saved = new ClaimFollowup();
        saved.setId(5L);
        when(claimFollowupRepository.save(any(ClaimFollowup.class))).thenReturn(saved);

        ClaimFollowup result = claimService.storeFollowup(1L, "q?", "a!", "user1", false);

        assertThat(result.getId()).isEqualTo(5L);
        ArgumentCaptor<ClaimFollowup> captor = ArgumentCaptor.forClass(ClaimFollowup.class);
        verify(claimFollowupRepository).save(captor.capture());
        assertThat(captor.getValue().getQuestion()).isEqualTo("q?");
        assertThat(captor.getValue().getAnswer()).isEqualTo("a!");
    }

    @Test
    void getClaim_throwsIfNotFound() {
        when(claimLogRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> claimService.getClaim(99L, "user1", false));
    }
}
