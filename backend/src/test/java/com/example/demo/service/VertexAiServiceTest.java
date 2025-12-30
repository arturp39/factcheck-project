package com.example.demo.service;

import com.example.demo.entity.Article;
import com.example.demo.util.PromptLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VertexAiServiceTest {

    @Mock
    private VertexAuthHelper authHelper;

    @Mock
    private VertexApiClient vertexApiClient;

    @Mock
    private PromptLoader promptLoader;

    @InjectMocks
    private VertexAiService vertexAiService;

    @SuppressWarnings("unchecked")
    private HttpResponse<String> mockVertexResponse(String body) {
        HttpResponse<String> resp = (HttpResponse<String>) Mockito.mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.body()).thenReturn(body);
        return resp;
    }

    private Article article(String title, String content, String source) {
        Article a = new Article();
        a.setTitle(title);
        a.setContent(content);
        a.setSource(source);
        return a;
    }

    @Test
    void askModel_usesFactcheckTemplateAndReturnsText() throws Exception {
        List<Article> evidence = List.of(article("Title A", "Text A", "Source A"));

        when(promptLoader.loadPrompt("factcheck"))
                .thenReturn("Claim: {{CLAIM}}\n\nEvidence:\n{{EVIDENCE}}");

        when(authHelper.chatEndpoint()).thenReturn("https://dummy-chat");

        String vertexBody = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          { "text": "Verdict: true\\nExplanation: OK" }
                        ]
                      }
                    }
                  ]
                }
                """;

        HttpResponse<String> resp = mockVertexResponse(vertexBody);

        when(vertexApiClient.postJson(eq("https://dummy-chat"), anyString()))
                .thenReturn(resp);

        String result = vertexAiService.askModel("Some claim", evidence);

        assertThat(result).contains("Verdict: true");
        verify(promptLoader).loadPrompt("factcheck");
    }

    @Test
    void analyzeBias_usesBiasTemplate() throws Exception {
        List<Article> evidence = List.of(article("T1", "C1", "S1"));

        when(promptLoader.loadPrompt("bias"))
                .thenReturn("Bias analysis for {{CLAIM}} with verdict {{VERDICT}} and evidence {{EVIDENCE}}");

        when(authHelper.chatEndpoint()).thenReturn("https://dummy-chat");

        String vertexBody = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          { "text": "Sources mainly from US outlets." }
                        ]
                      }
                    }
                  ]
                }
                """;

        HttpResponse<String> resp = mockVertexResponse(vertexBody);
        when(vertexApiClient.postJson(eq("https://dummy-chat"), anyString()))
                .thenReturn(resp);

        String result = vertexAiService.analyzeBias("Claim X", evidence, "true");

        assertThat(result).contains("Sources mainly from US outlets.");
        verify(promptLoader).loadPrompt("bias");
    }

    @Test
    void answerFollowUp_usesFollowupTemplate() throws Exception {
        List<Article> evidence = List.of(article("T1", "C1", "S1"));

        when(promptLoader.loadPrompt("followup"))
                .thenReturn("Follow-up for {{CLAIM}} / {{FOLLOWUP_QUESTION}}");

        when(authHelper.chatEndpoint()).thenReturn("https://dummy-chat");

        String vertexBody = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          { "text": "Short follow-up answer." }
                        ]
                      }
                    }
                  ]
                }
                """;

        HttpResponse<String> resp = mockVertexResponse(vertexBody);
        when(vertexApiClient.postJson(eq("https://dummy-chat"), anyString()))
                .thenReturn(resp);

        String result = vertexAiService.answerFollowUp(
                "Claim X",
                evidence,
                "mixed",
                "Because X and Y",
                "What about Z?"
        );

        assertThat(result).contains("Short follow-up answer.");
        verify(promptLoader).loadPrompt("followup");
    }

    @SuppressWarnings("unchecked")
    @Test
    void askModel_returnsErrorMessageOnNon2xx() throws Exception {
        when(promptLoader.loadPrompt("factcheck")).thenReturn("{{CLAIM}} {{EVIDENCE}}");
        when(authHelper.chatEndpoint()).thenReturn("https://dummy-chat");

        HttpResponse<String> resp = (HttpResponse<String>) Mockito.mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(500);
        when(resp.body()).thenReturn("fail");
        when(vertexApiClient.postJson(eq("https://dummy-chat"), anyString()))
                .thenReturn(resp);

        String result = vertexAiService.askModel("claim", List.of());
        assertThat(result).contains("Vertex AI error 500");
    }

    @SuppressWarnings("unchecked")
    @Test
    void analyzeBias_returnsErrorMessageOnFailure() throws Exception {
        when(promptLoader.loadPrompt("bias")).thenReturn("{{CLAIM}} {{EVIDENCE}} {{VERDICT}}");
        when(authHelper.chatEndpoint()).thenReturn("https://dummy-chat");

        HttpResponse<String> resp = (HttpResponse<String>) Mockito.mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(503);
        when(resp.body()).thenReturn("unavailable");
        when(vertexApiClient.postJson(eq("https://dummy-chat"), anyString()))
                .thenReturn(resp);

        String result = vertexAiService.analyzeBias("c", List.of(), "mixed");
        assertThat(result).contains("Bias analysis error 503");
    }

    @SuppressWarnings("unchecked")
    @Test
    void answerFollowUp_returnsErrorMessageOnFailure() throws Exception {
        when(promptLoader.loadPrompt("followup")).thenReturn("{{CLAIM}} {{FOLLOWUP_QUESTION}}");
        when(authHelper.chatEndpoint()).thenReturn("https://dummy-chat");

        HttpResponse<String> resp = (HttpResponse<String>) Mockito.mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(500);
        when(resp.body()).thenReturn("fail");
        when(vertexApiClient.postJson(eq("https://dummy-chat"), anyString()))
                .thenReturn(resp);

        String result = vertexAiService.answerFollowUp("c", List.of(), "v", "e", "q");
        assertThat(result).contains("Follow-up error 500");
    }
}