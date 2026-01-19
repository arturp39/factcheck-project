package com.factcheck.backend.service;

import com.factcheck.backend.dto.*;
import com.factcheck.backend.entity.ClaimFollowup;
import com.factcheck.backend.entity.ClaimLog;
import com.factcheck.backend.security.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimApiServiceTest {

    @Mock
    private ClaimService claimService;

    @Mock
    private ClaimWorkflowService claimWorkflowService;

    @Mock
    private CurrentUserService currentUserService;

    private ClaimApiService claimApiService;

    @BeforeEach
    void setUp() {
        claimApiService = new ClaimApiService(claimService, claimWorkflowService, currentUserService);
    }

    @Test
    void verify_mapsEvidenceAndSnippets() {
        when(currentUserService.requireUsername()).thenReturn("user");

        String longContent = "a".repeat(410);
        List<ArticleDto> evidence = List.of(
                new ArticleDto(1L, "Title1", longContent, "Source", null, "url", null, null, null),
                new ArticleDto(2L, "Title2", "  short  ", "Source", null, "url", null, null, null),
                new ArticleDto(3L, "Title3", "   ", "Source", null, "url", null, null, null),
                new ArticleDto(4L, "Title4", null, "Source", null, "url", null, null, null)
        );

        ClaimWorkflowService.VerifyResult verifyResult =
                new ClaimWorkflowService.VerifyResult("cid", 1L, "claim", "true", "expl", evidence);
        when(claimWorkflowService.verify("claim", "cid", "user")).thenReturn(verifyResult);

        VerifyResponse response = claimApiService.verify("claim", "cid");

        assertThat(response.evidence()).hasSize(4);
        assertThat(response.evidence().get(0).snippet()).endsWith("...");
        assertThat(response.evidence().get(0).snippet().length()).isEqualTo(400);
        assertThat(response.evidence().get(1).snippet()).isEqualTo("short");
        assertThat(response.evidence().get(2).snippet()).isNull();
        assertThat(response.evidence().get(3).snippet()).isNull();
    }

    @Test
    void listClaims_rejectsInvalidPageAndSize() {
        when(currentUserService.requireUsername()).thenReturn("user");
        when(currentUserService.isAdmin()).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> claimApiService.listClaims(-1, 10, "cid"));
        assertThrows(IllegalArgumentException.class, () -> claimApiService.listClaims(0, 0, "cid"));
        assertThrows(IllegalArgumentException.class, () -> claimApiService.listClaims(0, 201, "cid"));
    }

    @Test
    void listClaims_returnsPageMetadata() {
        when(currentUserService.requireUsername()).thenReturn("user");
        when(currentUserService.isAdmin()).thenReturn(false);

        ClaimLog log = new ClaimLog();
        log.setId(1L);
        log.setClaimText("claim");
        log.setCreatedAt(Instant.now());
        log.setVerdict("true");
        log.setExplanation("expl");

        when(claimService.listClaims(eq(PageRequest.of(0, 1)), eq("user"), eq(false)))
                .thenReturn(new PageImpl<>(List.of(log), PageRequest.of(0, 1), 1));

        ClaimsPageResponse response = claimApiService.listClaims(0, 1, null);

        assertThat(response.items()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.correlationId()).isNotBlank();
    }

    @Test
    void getClaim_mapsEntity() {
        when(currentUserService.requireUsername()).thenReturn("user");
        when(currentUserService.isAdmin()).thenReturn(false);

        ClaimLog log = new ClaimLog();
        log.setId(2L);
        log.setClaimText("claim");
        log.setCreatedAt(Instant.now());
        log.setVerdict("true");
        log.setExplanation("expl");
        log.setBiasAnalysis("bias");
        log.setModelAnswer("answer");

        when(claimService.getClaim(2L, "user", false)).thenReturn(log);

        ClaimResponse response = claimApiService.getClaim(2L, "cid");

        assertThat(response.claimId()).isEqualTo(2L);
        assertThat(response.biasAnalysis()).isEqualTo("bias");
        assertThat(response.modelAnswer()).isEqualTo("answer");
    }

    @Test
    void getEvidence_mapsContext() {
        when(currentUserService.requireUsername()).thenReturn("user");
        when(currentUserService.isAdmin()).thenReturn(false);

        List<ArticleDto> evidence = List.of(
                new ArticleDto(1L, "Title", "Content", "Source", LocalDateTime.now(), "url", null, null, null)
        );
        ClaimWorkflowService.ClaimContext context =
                new ClaimWorkflowService.ClaimContext("cid", 1L, "claim", Instant.now(), "true", "expl", "bias", evidence);

        when(claimWorkflowService.loadClaimContext(1L, "cid", "user", false)).thenReturn(context);

        EvidenceResponse response = claimApiService.getEvidence(1L, "cid");

        assertThat(response.evidence()).hasSize(1);
        assertThat(response.claimId()).isEqualTo(1L);
    }

    @Test
    void getEvidence_handlesNullEvidenceList() {
        when(currentUserService.requireUsername()).thenReturn("user");
        when(currentUserService.isAdmin()).thenReturn(false);

        ClaimWorkflowService.ClaimContext context =
                new ClaimWorkflowService.ClaimContext("cid", 1L, "claim", Instant.now(), "true", "expl", "bias", null);

        when(claimWorkflowService.loadClaimContext(1L, "cid", "user", false)).thenReturn(context);

        EvidenceResponse response = claimApiService.getEvidence(1L, "cid");

        assertThat(response.evidence()).isEmpty();
    }

    @Test
    void getHistory_mapsFollowups() {
        when(currentUserService.requireUsername()).thenReturn("user");
        when(currentUserService.isAdmin()).thenReturn(false);

        ClaimWorkflowService.ClaimContext context =
                new ClaimWorkflowService.ClaimContext("cid", 1L, "claim", Instant.now(), "true", "expl", "bias", List.of());
        ClaimFollowup followup = new ClaimFollowup();
        followup.setQuestion("q");
        followup.setAnswer("a");
        followup.setCreatedAt(Instant.now());
        ClaimWorkflowService.ConversationHistory history =
                new ClaimWorkflowService.ConversationHistory(context, List.of(followup));

        when(claimWorkflowService.loadConversationHistory(1L, "cid", "user", false)).thenReturn(history);

        ClaimHistoryResponse response = claimApiService.getHistory(1L, "cid");

        assertThat(response.followups()).hasSize(1);
        assertThat(response.followups().get(0).question()).isEqualTo("q");
    }

    @Test
    void followup_mapsResponse() {
        when(currentUserService.requireUsername()).thenReturn("user");
        when(currentUserService.isAdmin()).thenReturn(false);

        ClaimWorkflowService.FollowupResult result =
                new ClaimWorkflowService.FollowupResult(
                        "cid",
                        1L,
                        "claim",
                        "true",
                        "expl",
                        "bias",
                        List.of(),
                        "question",
                        "answer"
                );
        when(claimWorkflowService.followup(1L, "question", "cid", "user", false)).thenReturn(result);

        FollowupResponse response = claimApiService.followup(1L, "question", "cid");

        assertThat(response.answer()).isEqualTo("answer");
        assertThat(response.question()).isEqualTo("question");
    }

    @Test
    void bias_mapsResponse() {
        when(currentUserService.requireUsername()).thenReturn("user");
        when(currentUserService.isAdmin()).thenReturn(false);

        ClaimWorkflowService.BiasResult result =
                new ClaimWorkflowService.BiasResult("cid", 1L, "claim", "true", "bias", List.of());
        when(claimWorkflowService.bias(1L, "cid", "user", false)).thenReturn(result);

        BiasResponse response = claimApiService.bias(1L, "cid");

        assertThat(response.biasAnalysis()).isEqualTo("bias");
    }
}
