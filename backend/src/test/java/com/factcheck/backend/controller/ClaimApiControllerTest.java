package com.factcheck.backend.controller;

import com.factcheck.backend.dto.BiasResponse;
import com.factcheck.backend.dto.ClaimSummary;
import com.factcheck.backend.dto.ClaimsPageResponse;
import com.factcheck.backend.dto.EvidenceItem;
import com.factcheck.backend.dto.EvidenceResponse;
import com.factcheck.backend.dto.FollowupRequest;
import com.factcheck.backend.dto.FollowupResponse;
import com.factcheck.backend.dto.VerifyRequest;
import com.factcheck.backend.dto.VerifyResponse;
import com.factcheck.backend.service.ClaimApiService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClaimApiControllerTest {

    private AutoCloseable mocks;

    private ClaimApiService claimApiService;

    private ClaimApiController controller;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        claimApiService = mock(ClaimApiService.class);
        controller = new ClaimApiController(claimApiService);
    }

    @AfterEach
    void tearDown() throws Exception {
        MDC.clear();
        mocks.close();
    }

    @Test
    void verifyHappyPathReturnsJsonBody() {
        MDC.put("corrId", "cid-1");

        EvidenceItem evidence = new EvidenceItem(
                "Evidence title",
                "Evidence source",
                null,
                "Evidence snippet"
        );

        VerifyResponse response = new VerifyResponse(
                "cid-1",
                42L,
                "The sky is blue",
                "true",
                "because",
                List.of(evidence)
        );

        when(claimApiService.verify("The sky is blue", "cid-1")).thenReturn(response);

        var resp = controller.verify(new VerifyRequest("The sky is blue"));

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().correlationId()).isEqualTo("cid-1");
        assertThat(resp.getBody().claimId()).isEqualTo(42L);
        assertThat(resp.getBody().verdict()).isEqualTo("true");
        assertThat(resp.getBody().explanation()).isEqualTo("because");
        assertThat(resp.getBody().evidence()).hasSize(1);
    }

    @Test
    void listClaimsReturnsPagedSummaries() {
        MDC.put("corrId", "cid-list");

        ClaimSummary c1 = new ClaimSummary(
                1L, "c1", Instant.now(), "true", "e1"
        );
        ClaimsPageResponse respBody = new ClaimsPageResponse(
                "cid-list", 0, 20, 1, 1, List.of(c1)
        );
        when(claimApiService.listClaims(0, 20, "cid-list")).thenReturn(respBody);

        var resp = controller.listClaims(0, 20);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().correlationId()).isEqualTo("cid-list");
        assertThat(resp.getBody().items()).hasSize(1);
        assertThat(resp.getBody().items().getFirst().claimId()).isEqualTo(1L);
    }

    @Test
    void getEvidenceReturnsEvidenceForStoredClaim() {
        MDC.put("corrId", "cid-ev");

        EvidenceItem evidence = new EvidenceItem("t", "s", null, "c");
        EvidenceResponse response = new EvidenceResponse("cid-ev", 10L, "claim text", List.of(evidence));
        when(claimApiService.getEvidence(10L, "cid-ev")).thenReturn(response);

        var resp = controller.getEvidence(10L);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().correlationId()).isEqualTo("cid-ev");
        assertThat(resp.getBody().evidence()).hasSize(1);
    }

    @Test
    void followupRejectsEmptyQuestion() {
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Follow-up question must not be empty."))
                .when(claimApiService)
                .followup(org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString());
        MDC.put("corrId", "cid-x");

        assertThatThrownBy(() -> controller.followup(1L, new FollowupRequest(" ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void followupHappyPathReturnsAnswer() {
        MDC.put("corrId", "cid-2");

        EvidenceItem evidence = new EvidenceItem("t", "s", null, "c");

        FollowupResponse response = new FollowupResponse(
                "cid-2",
                6L,
                "claim text",
                "mixed",
                "expl",
                null,
                List.of(evidence),
                "why?",
                "answer here"
        );
        when(claimApiService.followup(6L, "why?", "cid-2")).thenReturn(response);

        var resp = controller.followup(6L, new FollowupRequest("why?"));

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().correlationId()).isEqualTo("cid-2");
        assertThat(resp.getBody().answer()).isEqualTo("answer here");
    }

    @Test
    void biasHappyPathReturnsBiasText() {
        MDC.put("corrId", "cid-3");

        BiasResponse respBody = new BiasResponse(
                "cid-3",
                9L,
                "claim text",
                "false",
                "bias text"
        );
        when(claimApiService.bias(9L, "cid-3")).thenReturn(respBody);

        var resp = controller.bias(9L);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().biasAnalysis()).isEqualTo("bias text");
    }

    @Test
    void verify_handlesNullRequestAndGeneratesCorrelationId() {
        when(claimApiService.verify(
                org.mockito.ArgumentMatchers.isNull(),
                anyString()))
                .thenAnswer(invocation -> new VerifyResponse(
                        invocation.getArgument(1),
                        1L,
                        null,
                        "unclear",
                        "n/a",
                        List.of()
                ));

        var resp = controller.verify(null);

        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().correlationId()).isNotBlank();
    }

    @Test
    void listClaims_generatesCorrelationIdWhenMdcBlank() {
        MDC.put("corrId", " ");

        when(claimApiService.listClaims(eq(0), eq(20), anyString()))
                .thenAnswer(invocation -> new ClaimsPageResponse(
                        invocation.getArgument(2),
                        0,
                        20,
                        0,
                        0,
                        List.of()
                ));

        var resp = controller.listClaims(0, 20);

        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().correlationId()).isNotBlank();
    }

    @Test
    void followup_handlesNullRequestAndGeneratesCorrelationId() {
        when(claimApiService.followup(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.isNull(),
                anyString()))
                .thenAnswer(invocation -> new FollowupResponse(
                        invocation.getArgument(2),
                        1L,
                        "claim",
                        "mixed",
                        "expl",
                        null,
                        List.of(),
                        null,
                        "answer"
                ));

        var resp = controller.followup(1L, null);

        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().correlationId()).isNotBlank();
    }
}
