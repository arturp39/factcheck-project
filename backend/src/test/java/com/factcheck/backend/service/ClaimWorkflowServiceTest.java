package com.factcheck.backend.service;

import com.factcheck.backend.dto.ArticleDto;
import com.factcheck.backend.entity.ClaimFollowup;
import com.factcheck.backend.entity.ClaimLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimWorkflowServiceTest {

    @Mock
    private ClaimService claimService;

    @Mock
    private VertexAiService vertexAiService;

    private ClaimWorkflowService workflowService;

    @BeforeEach
    void setUp() {
        workflowService = new ClaimWorkflowService(claimService, vertexAiService);
        ReflectionTestUtils.setField(workflowService, "claimMaxLength", 10);
    }

    @Test
    void verify_rejectsEmptyClaim() {
        assertThrows(IllegalArgumentException.class, () -> workflowService.verify(" ", null, "user"));
    }

    @Test
    void verify_rejectsNullClaim() {
        assertThrows(IllegalArgumentException.class, () -> workflowService.verify(null, null, "user"));
    }

    @Test
    void verify_rejectsTooLongClaim() {
        assertThrows(IllegalArgumentException.class, () -> workflowService.verify("too-long-claim", null, "user"));
    }

    @Test
    void verify_persistsClaimAndReturnsResult() {
        ClaimLog log = new ClaimLog();
        log.setId(42L);

        List<ArticleDto> evidence = List.of(
                new ArticleDto(1L, "Title", "Content", "Source", LocalDateTime.now(), "url", null, null, null)
        );

        when(claimService.saveClaim("claim", "user")).thenReturn(log);
        when(claimService.searchEvidence("claim", "cid")).thenReturn(evidence);
        when(vertexAiService.askModel("claim", evidence)).thenReturn("raw");
        when(claimService.storeModelAnswer(42L, "raw", "user", false))
                .thenReturn(new ClaimService.ParsedAnswer("true", "expl", "raw"));

        ClaimWorkflowService.VerifyResult result = workflowService.verify(" claim ", "cid", "user");

        assertThat(result.claimId()).isEqualTo(42L);
        assertThat(result.claim()).isEqualTo("claim");
        assertThat(result.verdict()).isEqualTo("true");
        assertThat(result.explanation()).isEqualTo("expl");
        assertThat(result.evidence()).hasSize(1);
    }

    @Test
    void followup_rejectsBlankQuestion() {
        assertThrows(IllegalArgumentException.class,
                () -> workflowService.followup(1L, " ", null, "user", false));
    }

    @Test
    void followup_generatesAnswerAndStoresFollowup() {
        ClaimLog log = new ClaimLog();
        log.setId(1L);
        log.setClaimText("claim");
        log.setVerdict("true");
        log.setExplanation("expl");
        log.setBiasAnalysis("bias");

        List<ArticleDto> evidence = List.of(
                new ArticleDto(1L, "Title", "Content", "Source", LocalDateTime.now(), "url", null, null, null)
        );

        when(claimService.getClaim(1L, "user", false)).thenReturn(log);
        when(claimService.searchEvidence("claim", "cid")).thenReturn(evidence);
        when(vertexAiService.answerFollowUp("claim", evidence, "true", "expl", "question"))
                .thenReturn("answer");

        ClaimWorkflowService.FollowupResult result =
                workflowService.followup(1L, " question ", "cid", "user", false);

        assertThat(result.question()).isEqualTo("question");
        assertThat(result.answer()).isEqualTo("answer");
        verify(claimService).storeFollowup(1L, "question", "answer", "user", false);
    }

    @Test
    void bias_generatesBiasAnalysisAndStores() {
        ClaimLog log = new ClaimLog();
        log.setId(1L);
        log.setClaimText("claim");
        log.setVerdict("true");

        List<ArticleDto> evidence = List.of(
                new ArticleDto(1L, "Title", "Content", "Source", LocalDateTime.now(), "url", null, null, null)
        );

        when(claimService.getClaim(1L, "user", false)).thenReturn(log);
        when(claimService.searchEvidence("claim", "cid")).thenReturn(evidence);
        when(vertexAiService.analyzeBias("claim", evidence, "true"))
                .thenReturn("bias");

        ClaimWorkflowService.BiasResult result =
                workflowService.bias(1L, "cid", "user", false);

        assertThat(result.biasAnalysis()).isEqualTo("bias");
        verify(claimService).storeBiasAnalysis(1L, "bias", "user", false);
    }

    @Test
    void loadClaimContext_mapsEvidence() {
        ClaimLog log = new ClaimLog();
        log.setId(1L);
        log.setClaimText("claim");
        log.setCreatedAt(Instant.now());
        log.setVerdict("true");
        log.setExplanation("expl");
        log.setBiasAnalysis("bias");

        List<ArticleDto> evidence = List.of(
                new ArticleDto(1L, "Title", "Content", "Source", LocalDateTime.now(), "url", null, null, null)
        );

        when(claimService.getClaim(1L, "user", false)).thenReturn(log);
        when(claimService.searchEvidence("claim", "cid")).thenReturn(evidence);

        ClaimWorkflowService.ClaimContext context =
                workflowService.loadClaimContext(1L, "cid", "user", false);

        assertThat(context.claim()).isEqualTo("claim");
        assertThat(context.evidence()).hasSize(1);
    }

    @Test
    void loadConversationHistory_returnsContextAndFollowups() {
        ClaimLog log = new ClaimLog();
        log.setId(1L);
        log.setClaimText("claim");
        log.setCreatedAt(Instant.now());

        when(claimService.getClaim(1L, "user", false)).thenReturn(log);
        when(claimService.searchEvidence(eq("claim"), anyString())).thenReturn(List.of());

        ClaimFollowup followup = new ClaimFollowup();
        followup.setQuestion("q");
        followup.setAnswer("a");
        when(claimService.listFollowups(1L, "user", false)).thenReturn(List.of(followup));

        ClaimWorkflowService.ConversationHistory history =
                workflowService.loadConversationHistory(1L, "cid", "user", false);

        assertThat(history.followups()).hasSize(1);
        assertThat(history.context().claimId()).isEqualTo(1L);
    }

    @Test
    void listRecentClaims_returnsEmptyWhenLimitIsZero() {
        List<ClaimLog> result = workflowService.listRecentClaims(0, "user", false);

        assertThat(result).isEmpty();
    }

    @Test
    void listRecentClaims_readsLatestClaims() {
        ClaimLog log = new ClaimLog();
        log.setId(1L);
        when(claimService.listClaims(
                eq(PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "createdAt"))),
                eq("user"),
                eq(false)))
                .thenReturn(new PageImpl<>(List.of(log)));

        List<ClaimLog> result = workflowService.listRecentClaims(2, "user", false);

        assertThat(result).hasSize(1);
    }
}
