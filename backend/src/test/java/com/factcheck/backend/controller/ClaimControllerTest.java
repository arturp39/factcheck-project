package com.factcheck.backend.controller;

import com.factcheck.backend.entity.Article;
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

    private ClaimController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new ClaimController(claimWorkflowService);
    }

    @Test
    void returnsErrorWhenClaimIsEmpty() {
        Model model = new ExtendedModelMap();

        doThrow(new IllegalArgumentException("Claim must not be empty."))
                .when(claimWorkflowService)
                .verify(eq("   "), any());

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
                .verify(eq(longClaim), any());

        String view = controller.verify(longClaim, model);

        assertThat(view).isEqualTo("index");
        assertThat(model.containsAttribute("error")).isTrue();
    }

    @Test
    void happyPathPopulatesModel() {
        Model model = new ExtendedModelMap();
        Article article = new Article();
        article.setTitle("A title");
        article.setContent("content");
        article.setSource("source");

        ClaimWorkflowService.VerifyResult result = new ClaimWorkflowService.VerifyResult(
                "cid-1",
                42L,
                "The sky is blue",
                "true",
                "because",
                List.of(article)
        );
        when(claimWorkflowService.verify("The sky is blue", null)).thenReturn(result);

        String view = controller.verify("The sky is blue", model);

        assertThat(view).isEqualTo("result");
        assertThat(model.getAttribute("claimId")).isEqualTo(42L);
        assertThat(model.containsAttribute("evidence")).isTrue();
        assertThat(model.getAttribute("verdict")).isEqualTo("true");
        assertThat(model.getAttribute("explanation")).isEqualTo("because");
    }

    @Test
    void followupReturnsResultWhenQuestionEmpty() {
        Article article = new Article();
        article.setTitle("t");
        article.setContent("c");
        article.setSource("s");

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
        when(claimWorkflowService.loadClaimContext(5L, null)).thenReturn(context);

        Model model = new ExtendedModelMap();
        String view = controller.followup(5L, "  ", model);

        assertThat(view).isEqualTo("result");
        assertThat(model.getAttribute("error")).isEqualTo("Follow-up question must not be empty.");
        assertThat(model.getAttribute("claim")).isEqualTo("claim text");
        assertThat(model.getAttribute("verdict")).isEqualTo("true");
    }

    @Test
    void followupHappyPathAddsAnswer() {
        Article article = new Article();
        article.setTitle("t");
        article.setContent("c");
        article.setSource("s");

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
        when(claimWorkflowService.followup(6L, "why?", null)).thenReturn(result);

        Model model = new ExtendedModelMap();
        String view = controller.followup(6L, "why?", model);

        assertThat(view).isEqualTo("result");
        assertThat(model.getAttribute("followupAnswer")).isEqualTo("answer here");
        assertThat(model.getAttribute("followupQuestion")).isEqualTo("why?");
    }

    @Test
    void analyzeBiasAddsBiasText() {
        Article article = new Article();
        article.setTitle("t");
        article.setContent("c");
        article.setSource("s");

        ClaimWorkflowService.BiasResult result = new ClaimWorkflowService.BiasResult(
                "cid-bias",
                9L,
                "claim text",
                "false",
                "bias text",
                List.of(article)
        );
        when(claimWorkflowService.bias(9L, null)).thenReturn(result);

        Model model = new ExtendedModelMap();
        String view = controller.analyzeBias(9L, model);

        assertThat(view).isEqualTo("result");
        assertThat(model.getAttribute("biasAnalysis")).isEqualTo("bias text");
    }
}
