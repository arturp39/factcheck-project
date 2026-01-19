package com.factcheck.backend.controller;

import com.factcheck.backend.dto.ArticleDto;
import com.factcheck.backend.security.CurrentUserService;
import com.factcheck.backend.service.ClaimWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

class ClaimControllerTest {

    @Mock
    private ClaimWorkflowService claimWorkflowService;

    @Mock
    private CurrentUserService currentUserService;

    private ClaimController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new ClaimController(claimWorkflowService, currentUserService);
    }

    @Test
    void returnsErrorWhenClaimIsEmpty() {
        Model model = new ExtendedModelMap();

        doThrow(new IllegalArgumentException("Claim must not be empty."))
                .when(claimWorkflowService)
                .verify(eq("   "), any(), eq("user1"));
        when(currentUserService.requireUsername()).thenReturn("user1");

        String view = controller.verify("   ", model);

        assertThat(view).isEqualTo("index");
        assertThat(model.containsAttribute("error")).isTrue();
    }

    @Test
    void rejectsTooLongClaim() {
        Model model = new ExtendedModelMap();
        String longClaim = "x".repeat(401);

        doThrow(new IllegalArgumentException("Claim is too long. Please keep it under 400 characters."))
                .when(claimWorkflowService)
                .verify(eq(longClaim), any(), eq("user1"));
        when(currentUserService.requireUsername()).thenReturn("user1");

        String view = controller.verify(longClaim, model);

        assertThat(view).isEqualTo("index");
        assertThat(model.containsAttribute("error")).isTrue();
    }

    @Test
    void happyPathPopulatesModel() {
        Model model = new ExtendedModelMap();

        ArticleDto article = new ArticleDto(
                null,
                "A title",
                "content",
                "source",
                null,
                null,
                null,
                null,
                null
        );

        ClaimWorkflowService.VerifyResult result = new ClaimWorkflowService.VerifyResult(
                "cid-1",
                42L,
                "The sky is blue",
                "true",
                "because",
                List.of(article)
        );
        when(currentUserService.requireUsername()).thenReturn("user1");
        when(claimWorkflowService.verify("The sky is blue", null, "user1")).thenReturn(result);

        String view = controller.verify("The sky is blue", model);

        assertThat(view).isEqualTo("result");
        assertThat(model.getAttribute("claimId")).isEqualTo(42L);
        assertThat(model.containsAttribute("evidence")).isTrue();
        assertThat(model.getAttribute("verdict")).isEqualTo("true");
        assertThat(model.getAttribute("explanation")).isEqualTo("because");
    }

    @Test
    void verify_handlesNullEvidence() {
        ClaimWorkflowService.VerifyResult result = new ClaimWorkflowService.VerifyResult(
                "cid-1",
                42L,
                "The sky is blue",
                "true",
                "because",
                null
        );
        when(currentUserService.requireUsername()).thenReturn("user1");
        when(claimWorkflowService.verify("The sky is blue", null, "user1")).thenReturn(result);

        Model model = new ExtendedModelMap();
        String view = controller.verify("The sky is blue", model);

        assertThat(view).isEqualTo("result");
        @SuppressWarnings("unchecked")
        List<ArticleDto> evidence = (List<ArticleDto>) model.getAttribute("evidence");
        assertThat(evidence).isEmpty();
        @SuppressWarnings("unchecked")
        List<Object> evidenceViews = (List<Object>) model.getAttribute("evidenceViews");
        assertThat(evidenceViews).isEmpty();
    }

    @Test
    void followupReturnsResultWhenQuestionEmpty() {
        ArticleDto article = new ArticleDto(
                null,
                "t",
                "c",
                "s",
                null,
                null,
                null,
                null,
                null
        );

        ClaimWorkflowService.ClaimContext context = new ClaimWorkflowService.ClaimContext(
                "cid-ctx",
                5L,
                "claim text",
                null,
                "true",
                "because",
                null,
                List.of(article)
        );
        when(currentUserService.requireUsername()).thenReturn("user1");
        when(claimWorkflowService.loadClaimContext(5L, null, "user1", false)).thenReturn(context);
        when(claimWorkflowService.listFollowups(5L, "user1", false)).thenReturn(List.of());

        Model model = new ExtendedModelMap();
        String view = controller.followup(5L, "  ", model);

        assertThat(view).isEqualTo("result");
        assertThat(model.getAttribute("error")).isEqualTo("Follow-up question must not be empty.");
        assertThat(model.getAttribute("claim")).isEqualTo("claim text");
        assertThat(model.getAttribute("verdict")).isEqualTo("true");
    }

    @Test
    void followupTreatsNullQuestionAsEmpty() {
        ArticleDto article = new ArticleDto(
                null,
                "t",
                "c",
                "s",
                null,
                null,
                null,
                null,
                null
        );

        ClaimWorkflowService.ClaimContext context = new ClaimWorkflowService.ClaimContext(
                "cid-ctx",
                5L,
                "claim text",
                null,
                "true",
                "because",
                null,
                List.of(article)
        );
        when(currentUserService.requireUsername()).thenReturn("user1");
        when(claimWorkflowService.loadClaimContext(5L, null, "user1", false)).thenReturn(context);
        when(claimWorkflowService.listFollowups(5L, "user1", false)).thenReturn(List.of());

        Model model = new ExtendedModelMap();
        String view = controller.followup(5L, null, model);

        assertThat(view).isEqualTo("result");
        assertThat(model.getAttribute("error")).isEqualTo("Follow-up question must not be empty.");
    }

    @Test
    void followupHappyPathAddsAnswer() {
        ArticleDto article = new ArticleDto(
                null,
                "t",
                "c",
                "s",
                null,
                null,
                null,
                null,
                null
        );

        ClaimWorkflowService.FollowupResult result = new ClaimWorkflowService.FollowupResult(
                "cid-follow",
                6L,
                "claim text",
                "mixed",
                "expl",
                null,
                List.of(article),
                "why?",
                "answer here"
        );
        when(currentUserService.requireUsername()).thenReturn("user1");
        when(claimWorkflowService.followup(6L, "why?", null, "user1", false)).thenReturn(result);
        when(claimWorkflowService.listFollowups(6L, "user1", false)).thenReturn(List.of());

        Model model = new ExtendedModelMap();
        String view = controller.followup(6L, "why?", model);

        assertThat(view).isEqualTo("result");
        assertThat(model.getAttribute("followupAnswer")).isEqualTo("answer here");
        assertThat(model.getAttribute("followupQuestion")).isEqualTo("why?");
    }

    @Test
    void analyzeBiasAddsBiasText() {
        ArticleDto article = new ArticleDto(
                null,
                "t",
                "c",
                "s",
                null,
                null,
                null,
                null,
                null
        );

        ClaimWorkflowService.BiasResult result = new ClaimWorkflowService.BiasResult(
                "cid-bias",
                9L,
                "claim text",
                "false",
                "expl",
                "bias text",
                List.of(article)
        );
        when(currentUserService.requireUsername()).thenReturn("user1");
        when(claimWorkflowService.bias(9L, null, "user1", false)).thenReturn(result);
        when(claimWorkflowService.listFollowups(9L, "user1", false)).thenReturn(List.of());

        Model model = new ExtendedModelMap();
        String view = controller.analyzeBias(9L, model);

        assertThat(view).isEqualTo("result");
        assertThat(model.getAttribute("biasAnalysis")).isEqualTo("bias text");
        assertThat(model.getAttribute("explanation")).isEqualTo("expl");
    }

    @Test
    void index_populatesRecentClaims() {
        when(currentUserService.requireUsername()).thenReturn("user1");
        when(claimWorkflowService.listRecentClaims(10, "user1", false)).thenReturn(List.of());

        Model model = new ExtendedModelMap();
        String view = controller.index(model);

        assertThat(view).isEqualTo("index");
        assertThat(model.containsAttribute("recentClaims")).isTrue();
    }

    @Test
    void history_populatesModel() {
        ArticleDto article = new ArticleDto(
                11L,
                "t",
                "c",
                "s",
                LocalDateTime.now(),
                "url",
                null,
                null,
                null
        );
        ClaimWorkflowService.ClaimContext context = new ClaimWorkflowService.ClaimContext(
                "cid-h",
                7L,
                "claim",
                Instant.now(),
                "true",
                "expl",
                "bias",
                List.of(article)
        );
        when(currentUserService.requireUsername()).thenReturn("user1");
        when(claimWorkflowService.loadConversationHistory(7L, null, "user1", false))
                .thenReturn(new ClaimWorkflowService.ConversationHistory(context, List.of()));

        Model model = new ExtendedModelMap();
        String view = controller.history(7L, model);

        assertThat(view).isEqualTo("result");
        assertThat(model.getAttribute("claim")).isEqualTo("claim");
    }

    @Test
    void verify_buildsEvidenceViewsWithGrouping() {
        String longContent = "x".repeat(300);
        ArticleDto grouped1 = new ArticleDto(
                1L,
                "A",
                longContent,
                "S1",
                LocalDateTime.now(),
                "url-1",
                null,
                null,
                null
        );
        ArticleDto grouped2 = new ArticleDto(
                1L,
                "B",
                "short",
                "S1",
                LocalDateTime.now(),
                "url-1",
                null,
                null,
                null
        );
        ArticleDto grouped3 = new ArticleDto(
                1L,
                "C",
                "short",
                "S1",
                LocalDateTime.now(),
                "url-1",
                null,
                null,
                null
        );
        ArticleDto byUrl = new ArticleDto(
                null,
                "D",
                "short",
                "S2",
                LocalDateTime.now(),
                "http://example.com/d",
                null,
                null,
                null
        );
        ArticleDto fallback = new ArticleDto(
                null,
                "E",
                "short",
                "S3",
                LocalDateTime.now(),
                null,
                null,
                null,
                null
        );
        List<ArticleDto> evidence = List.of(grouped1, grouped2, grouped3, byUrl, fallback);

        ClaimWorkflowService.VerifyResult result = new ClaimWorkflowService.VerifyResult(
                "cid",
                12L,
                "claim",
                "true",
                "expl",
                evidence
        );
        when(currentUserService.requireUsername()).thenReturn("user1");
        when(claimWorkflowService.verify("claim", null, "user1")).thenReturn(result);

        Model model = new ExtendedModelMap();
        String view = controller.verify("claim", model);

        assertThat(view).isEqualTo("result");
        @SuppressWarnings("unchecked")
        List<Object> views = (List<Object>) model.getAttribute("evidenceViews");
        assertThat(views).hasSize(3);
        assertThat(views.get(0).toString()).contains("grouped=true").contains("chunkCount=3");
        assertThat(views.get(0).toString()).contains("showToggle=true");
    }

    @Test
    void buildEvidenceViews_handlesNullList() {
        @SuppressWarnings("unchecked")
        List<Object> views = (List<Object>) ReflectionTestUtils.invokeMethod(controller, "buildEvidenceViews", new Object[]{null});

        assertThat(views).isEmpty();
    }

    @Test
    void groupKey_handlesNullArticle() {
        String key = ReflectionTestUtils.invokeMethod(controller, "groupKey", new Object[]{null});

        assertThat(key).isEqualTo("unknown");
    }

    @Test
    void groupKey_fallsBackWhenUrlBlank() {
        ArticleDto article = new ArticleDto(
                null,
                "Title",
                "content",
                "Source",
                null,
                "  ",
                null,
                null,
                null
        );

        String key = ReflectionTestUtils.invokeMethod(controller, "groupKey", article);

        assertThat(key).isEqualTo("title:Title|source:Source|published:");
    }
}
