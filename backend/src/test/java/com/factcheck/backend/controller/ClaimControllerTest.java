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
    }
}
