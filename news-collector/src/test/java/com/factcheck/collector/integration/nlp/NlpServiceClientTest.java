package com.factcheck.collector.integration.nlp;

import com.factcheck.collector.exception.NlpServiceException;
import com.factcheck.collector.integration.nlp.dto.EmbedRequest;
import com.factcheck.collector.integration.nlp.dto.EmbedResponse;
import com.factcheck.collector.integration.nlp.dto.PreprocessRequest;
import com.factcheck.collector.integration.nlp.dto.PreprocessResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NlpServiceClientTest {

    private RestTemplate restTemplate;
    private NlpServiceClient client;

    @BeforeEach
    void setUp() throws Exception {
        restTemplate = mock(RestTemplate.class);
        client = new NlpServiceClient(restTemplate);

        Field f = NlpServiceClient.class.getDeclaredField("baseUrl");
        f.setAccessible(true);
        f.set(client, "http://nlp-service");
    }

    @Test
    void preprocess_successfulResponse_returnsBody() {
        PreprocessResponse body = new PreprocessResponse();
        body.setSentences(List.of("One.", "Two."));

        ResponseEntity<PreprocessResponse> resp =
                new ResponseEntity<>(body, HttpStatus.OK);

        when(restTemplate.exchange(
                eq("http://nlp-service/preprocess"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(PreprocessResponse.class)
        )).thenReturn(resp);

        PreprocessResponse result = client.preprocess("text", "cid-1");

        assertThat(result.getSentences()).containsExactly("One.", "Two.");

        ArgumentCaptor<HttpEntity<PreprocessRequest>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("http://nlp-service/preprocess"),
                eq(HttpMethod.POST),
                captor.capture(),
                eq(PreprocessResponse.class)
        );

        HttpHeaders headers = captor.getValue().getHeaders();
        assertThat(headers.getFirst("X-Correlation-Id")).isNotBlank();
    }

    @Test
    void preprocess_restClientException_wrappedIntoNlpServiceException() {
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(PreprocessResponse.class)
        )).thenThrow(new RestClientException("boom"));

        assertThatThrownBy(() -> client.preprocess("text", UUID.randomUUID().toString()))
                .isInstanceOf(NlpServiceException.class)
                .hasMessageContaining("NLP preprocess failed");
    }

    @Test
    void embed_successfulResponse_returnsEmbeddings() {
        EmbedResponse body = new EmbedResponse();
        body.setEmbeddings(List.of(List.of(0.1, 0.2)));

        ResponseEntity<EmbedResponse> resp =
                new ResponseEntity<>(body, HttpStatus.OK);

        when(restTemplate.exchange(
                eq("http://nlp-service/embed"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(EmbedResponse.class)
        )).thenReturn(resp);

        EmbedRequest req = new EmbedRequest();
        req.setTexts(List.of("chunk1"));
        req.setCorrelationId("cid-xyz");

        EmbedResponse result = client.embed(req);

        assertThat(result.getEmbeddings()).hasSize(1);
        assertThat(result.getEmbeddings().getFirst()).containsExactly(0.1, 0.2);
    }
    @Test
    void preprocess_whenRestTemplateReturnsNullResponse_throws() {
        when(restTemplate.exchange(
                eq("http://nlp-service/preprocess"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(PreprocessResponse.class)
        )).thenReturn(null);

        assertThatThrownBy(() -> client.preprocess("text", "cid-1"))
                .isInstanceOf(NlpServiceException.class)
                .hasMessageContaining("HTTP status null response");
    }

    @Test
    void preprocess_whenNon2xxStatus_throws() {
        ResponseEntity<PreprocessResponse> resp =
                new ResponseEntity<>(new PreprocessResponse(), HttpStatus.INTERNAL_SERVER_ERROR);

        when(restTemplate.exchange(
                eq("http://nlp-service/preprocess"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(PreprocessResponse.class)
        )).thenReturn(resp);

        assertThatThrownBy(() -> client.preprocess("text", "cid-1"))
                .isInstanceOf(NlpServiceException.class)
                .hasMessageContaining("HTTP status 500");
    }

    @Test
    void preprocess_when2xxButBodyNull_throws() {
        ResponseEntity<PreprocessResponse> resp =
                new ResponseEntity<>(null, HttpStatus.OK);

        when(restTemplate.exchange(
                eq("http://nlp-service/preprocess"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(PreprocessResponse.class)
        )).thenReturn(resp);

        assertThatThrownBy(() -> client.preprocess("text", "cid-1"))
                .isInstanceOf(NlpServiceException.class)
                .hasMessageContaining("empty response body");
    }

    @Test
    void preprocess_whenCorrelationIdBlank_generatesHeader() {
        PreprocessResponse body = new PreprocessResponse();
        body.setSentences(List.of("S"));

        ResponseEntity<PreprocessResponse> resp =
                new ResponseEntity<>(body, HttpStatus.OK);

        when(restTemplate.exchange(
                eq("http://nlp-service/preprocess"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(PreprocessResponse.class)
        )).thenReturn(resp);

        client.preprocess("text", "   ");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<PreprocessRequest>> captor = ArgumentCaptor.forClass(HttpEntity.class);

        verify(restTemplate).exchange(
                eq("http://nlp-service/preprocess"),
                eq(HttpMethod.POST),
                captor.capture(),
                eq(PreprocessResponse.class)
        );

        HttpHeaders headers = captor.getValue().getHeaders();
        assertThat(headers.getFirst("X-Correlation-Id")).isNotBlank();
    }

    @Test
    void embed_whenNon2xxStatus_throws() {
        EmbedResponse body = new EmbedResponse();
        body.setEmbeddings(List.of(List.of(0.1)));

        ResponseEntity<EmbedResponse> resp =
                new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);

        when(restTemplate.exchange(
                eq("http://nlp-service/embed"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(EmbedResponse.class)
        )).thenReturn(resp);

        EmbedRequest req = new EmbedRequest();
        req.setTexts(List.of("t"));
        req.setCorrelationId("cid");

        assertThatThrownBy(() -> client.embed(req))
                .isInstanceOf(NlpServiceException.class)
                .hasMessageContaining("HTTP status 400");
    }

    @Test
    void embed_when2xxButBodyNull_throws() {
        ResponseEntity<EmbedResponse> resp =
                new ResponseEntity<>(null, HttpStatus.OK);

        when(restTemplate.exchange(
                eq("http://nlp-service/embed"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(EmbedResponse.class)
        )).thenReturn(resp);

        EmbedRequest req = new EmbedRequest();
        req.setTexts(List.of("t"));
        req.setCorrelationId("cid");

        assertThatThrownBy(() -> client.embed(req))
                .isInstanceOf(NlpServiceException.class)
                .hasMessageContaining("empty response body");
    }

    @Test
    void embed_whenRestClientException_wrappedIntoNlpServiceException() {
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(EmbedResponse.class)
        )).thenThrow(new RestClientException("boom"));

        EmbedRequest req = new EmbedRequest();
        req.setTexts(List.of("t"));
        req.setCorrelationId("cid");

        assertThatThrownBy(() -> client.embed(req))
                .isInstanceOf(NlpServiceException.class)
                .hasMessageContaining("NLP embed failed")
                .hasCauseInstanceOf(RestClientException.class);
    }

    @Test
    void embed_whenCorrelationIdBlank_generatesHeader() {
        EmbedResponse body = new EmbedResponse();
        body.setEmbeddings(List.of(List.of(0.1)));

        ResponseEntity<EmbedResponse> resp =
                new ResponseEntity<>(body, HttpStatus.OK);

        when(restTemplate.exchange(
                eq("http://nlp-service/embed"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(EmbedResponse.class)
        )).thenReturn(resp);

        EmbedRequest req = new EmbedRequest();
        req.setTexts(List.of("t"));
        req.setCorrelationId("   ");

        client.embed(req);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<EmbedRequest>> captor = ArgumentCaptor.forClass(HttpEntity.class);

        verify(restTemplate).exchange(
                eq("http://nlp-service/embed"),
                eq(HttpMethod.POST),
                captor.capture(),
                eq(EmbedResponse.class)
        );

        HttpHeaders headers = captor.getValue().getHeaders();
        assertThat(headers.getFirst("X-Correlation-Id")).isNotBlank();
    }
}