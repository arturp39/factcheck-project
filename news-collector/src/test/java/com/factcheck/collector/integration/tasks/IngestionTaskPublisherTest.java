package com.factcheck.collector.integration.tasks;

import com.factcheck.collector.dto.IngestionTaskRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IngestionTaskPublisherTest {

    private IngestionTaskPublisher publisher;
    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        publisher = new IngestionTaskPublisher(new ObjectMapper());

        httpClient = mock(HttpClient.class);
        setField(publisher, "httpClient", httpClient);

        setField(publisher, "projectId", "p1");
        setField(publisher, "location", "l1");
        setField(publisher, "queue", "q1");
        setField(publisher, "targetUrl", "https://example.com/task-handler");
        setField(publisher, "serviceAccountEmail", "");
        setField(publisher, "metadataUrl", "http://metadata/token");
        setField(publisher, "accessTokenOverride", "");
    }

    @Test
    void enqueueThrowsWhenConfigMissing() {
        setField(publisher, "queue", "   ");

        assertThatThrownBy(() -> publisher.enqueueIngestionTask(new IngestionTaskRequest(1L, 2L, "c")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cloud Tasks config missing");

        verifyNoInteractions(httpClient);
    }

    @Test
    void enqueueThrowsWhenRequestMissingIds() {
        assertThatThrownBy(() -> publisher.enqueueIngestionTask(new IngestionTaskRequest(null, 2L, "c")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runId and sourceEndpointId are required");

        assertThatThrownBy(() -> publisher.enqueueIngestionTask(new IngestionTaskRequest(1L, null, "c")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runId and sourceEndpointId are required");

        verifyNoInteractions(httpClient);
    }

    @Test
    void enqueueUsesAccessTokenOverrideAndSendsAuthorizationHeader() throws Exception {
        setField(publisher, "accessTokenOverride", "override-token");
        setField(publisher, "serviceAccountEmail", "svc@example.com");

        HttpResponse<String> ok = mock(HttpResponse.class);
        when(ok.statusCode()).thenReturn(200);
        when(ok.body()).thenReturn("{\"ok\":true}");

        when(httpClient.send(any(HttpRequest.class), anyStringBodyHandler()))
                .thenReturn(ok);

        publisher.enqueueIngestionTask(new IngestionTaskRequest(10L, 20L, "corr-1"));

        ArgumentCaptor<HttpRequest> reqCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(reqCaptor.capture(), anyStringBodyHandler());

        HttpRequest req = reqCaptor.getValue();
        assertThat(req.uri().toString()).contains("https://cloudtasks.googleapis.com/v2/");
        assertThat(req.headers().firstValue("Authorization")).hasValue("Bearer override-token");
        assertThat(req.headers().firstValue("Content-Type")).hasValue("application/json");
    }

    @Test
    void enqueueFetchesMetadataTokenOnceAndCachesIt() throws Exception {
        HttpResponse<String> metadataResp = mock(HttpResponse.class);
        when(metadataResp.statusCode()).thenReturn(200);
        when(metadataResp.body()).thenReturn("{\"access_token\":\"abc\",\"expires_in\":3600}");

        HttpResponse<String> enqueueResp = mock(HttpResponse.class);
        when(enqueueResp.statusCode()).thenReturn(200);
        when(enqueueResp.body()).thenReturn("{\"ok\":true}");

        ArgumentCaptor<HttpRequest> reqCaptor = ArgumentCaptor.forClass(HttpRequest.class);

        when(httpClient.send(reqCaptor.capture(), anyStringBodyHandler()))
                .thenAnswer(inv -> {
                    HttpRequest req = inv.getArgument(0, HttpRequest.class);
                    URI uri = req.uri();
                    if ("http".equals(uri.getScheme()) && "metadata".equals(uri.getHost())) {
                        return metadataResp;
                    }
                    return enqueueResp;
                });

        publisher.enqueueIngestionTask(new IngestionTaskRequest(1L, 2L, "corr-1"));
        publisher.enqueueIngestionTask(new IngestionTaskRequest(1L, 2L, "corr-1"));

        List<HttpRequest> sent = reqCaptor.getAllValues();

        long metadataCalls = sent.stream()
                .filter(r -> r.uri().toString().equals("http://metadata/token"))
                .count();

        long enqueueCalls = sent.stream()
                .filter(r -> r.uri().toString().contains("https://cloudtasks.googleapis.com/v2/"))
                .count();

        assertThat(metadataCalls).isEqualTo(1);
        assertThat(enqueueCalls).isEqualTo(2);

        HttpRequest firstEnqueue = sent.stream()
                .filter(r -> r.uri().toString().contains("https://cloudtasks.googleapis.com/v2/"))
                .findFirst()
                .orElseThrow();

        assertThat(firstEnqueue.headers().firstValue("Authorization")).hasValue("Bearer abc");
    }

    @Test
    void enqueueWrapsNon2xxAsIllegalStateException() throws Exception {
        setField(publisher, "accessTokenOverride", "override-token");

        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(500);
        when(resp.body()).thenReturn("nope");

        when(httpClient.send(any(HttpRequest.class), anyStringBodyHandler()))
                .thenReturn(resp);

        assertThatThrownBy(() -> publisher.enqueueIngestionTask(new IngestionTaskRequest(1L, 2L, "c")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cloud Tasks enqueue failed")
                .hasCauseInstanceOf(IllegalStateException.class);

        verify(httpClient).send(any(HttpRequest.class), anyStringBodyHandler());
    }

    private static HttpResponse.BodyHandler<String> anyStringBodyHandler() {
        return ArgumentMatchers.any();
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }
}