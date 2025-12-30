package com.example.demo.controller;

import com.example.demo.entity.Article;
import com.example.demo.entity.ClaimLog;
import com.example.demo.service.ClaimService;
import com.example.demo.service.VertexAiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClaimControllerTest {

    @Mock
    private ClaimService claimService;
    @Mock
    private VertexAiService vertexAiService;

    private ClaimController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new ClaimController(claimService, vertexAiService, 400);
    }

    @Test
    void returnsErrorWhenClaimIsEmpty() {
        Model model = new ExtendedModelMap();

        String view = controller.verify("   ", model);

        assertThat(view).isEqualTo("index");
        assertThat(model.containsAttribute("error")).isTrue();
    }

    @Test
    void rejectsTooLongClaim() {
        Model model = new ExtendedModelMap();
        String longClaim = "x".repeat(401);

        String view = controller.verify(longClaim, model);

        assertThat(view).isEqualTo("index");
        assertThat(model.containsAttribute("error")).isTrue();
    }

    @Test
    void happyPathPopulatesModel() {
        Model model = new ExtendedModelMap();
        ClaimLog saved = new ClaimLog();
        saved.setId(42L);
        saved.setClaimText("The sky is blue");

        Article article = new Article();
        article.setTitle("A title");
        article.setContent("content");
        article.setSource("source");

        when(claimService.saveClaim("The sky is blue")).thenReturn(saved);
        when(claimService.searchEvidence("The sky is blue")).thenReturn(List.of(article));
        when(vertexAiService.askModel(eq("The sky is blue"), any())).thenReturn("Verdict: true\nExplanation: ok");
        when(claimService.storeModelAnswer(eq(42L), any()))
                .thenReturn(new ClaimService.ParsedAnswer("true", "because", "raw"));

        String view = controller.verify("The sky is blue", model);

        assertThat(view).isEqualTo("result");
        assertThat(model.getAttribute("claimId")).isEqualTo(42L);
        assertThat(model.containsAttribute("evidence")).isTrue();
        assertThat(model.getAttribute("verdict")).isEqualTo("true");
        assertThat(model.getAttribute("explanation")).isEqualTo("because");

        ArgumentCaptor<String> answerCaptor = ArgumentCaptor.forClass(String.class);
        verify(claimService).storeModelAnswer(eq(42L), answerCaptor.capture());
        assertThat(answerCaptor.getValue()).contains("Explanation");
    }

    @Test
    void followupReturnsResultWhenQuestionEmpty() {
        ClaimLog log = new ClaimLog();
        log.setId(5L);
        log.setClaimText("claim text");
        log.setVerdict("true");
        log.setExplanation("because");

        Article article = new Article();
        article.setTitle("t");
        article.setContent("c");
        article.setSource("s");

        when(claimService.getClaim(5L)).thenReturn(log);
        when(claimService.searchEvidence("claim text")).thenReturn(List.of(article));

        Model model = new ExtendedModelMap();
        String view = controller.followup(5L, "  ", model);

        assertThat(view).isEqualTo("result");
        assertThat(model.getAttribute("error")).isEqualTo("Follow-up question must not be empty.");
        assertThat(model.getAttribute("claim")).isEqualTo("claim text");
        assertThat(model.getAttribute("verdict")).isEqualTo("true");
    }

    @Test
    void followupHappyPathAddsAnswer() {
        ClaimLog log = new ClaimLog();
        log.setId(6L);
        log.setClaimText("claim text");
        log.setVerdict("mixed");
        log.setExplanation("expl");

        Article article = new Article();
        article.setTitle("t");
        article.setContent("c");
        article.setSource("s");

        when(claimService.getClaim(6L)).thenReturn(log);
        when(claimService.searchEvidence("claim text")).thenReturn(List.of(article));
        when(vertexAiService.answerFollowUp("claim text", List.of(article), "mixed", "expl", "why?"))
                .thenReturn("answer here");

        Model model = new ExtendedModelMap();
        String view = controller.followup(6L, "why?", model);

        assertThat(view).isEqualTo("result");
        assertThat(model.getAttribute("followupAnswer")).isEqualTo("answer here");
        assertThat(model.getAttribute("followupQuestion")).isEqualTo("why?");
    }

    @Test
    void analyzeBiasAddsBiasText() {
        ClaimLog log = new ClaimLog();
        log.setId(9L);
        log.setClaimText("claim text");
        log.setVerdict("false");
        log.setExplanation("because");

        Article article = new Article();
        article.setTitle("t");
        article.setContent("c");
        article.setSource("s");

        when(claimService.getClaim(9L)).thenReturn(log);
        when(claimService.searchEvidence("claim text")).thenReturn(List.of(article));
        when(vertexAiService.analyzeBias("claim text", List.of(article), "false")).thenReturn("bias text");

        Model model = new ExtendedModelMap();
        String view = controller.analyzeBias(9L, model);

        assertThat(view).isEqualTo("result");
        assertThat(model.getAttribute("biasAnalysis")).isEqualTo("bias text");
        verify(claimService).storeBiasAnalysis(9L, "bias text");
    }
}
