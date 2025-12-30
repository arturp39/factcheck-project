package com.example.demo.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ExtendedModelMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void returnsIndexViewForHtmlRequests() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getHeader("Accept")).thenReturn("text/html");
        when(request.getRequestURI()).thenReturn("/verify");

        ExtendedModelMap model = new ExtendedModelMap();
        Object result = handler.handleBadRequest(new IllegalArgumentException("bad"), request, model);

        assertThat(result).isEqualTo("index");
        assertThat(model.get("error")).isEqualTo("bad");
    }

    @Test
    void returnsJsonResponseForApiRequests() {
        MDC.put("corrId", "cid-1");
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getHeader("Accept")).thenReturn("application/json");
        when(request.getRequestURI()).thenReturn("/api/test");

        Object result =
                handler.handleBadRequest(new IllegalArgumentException("bad"), request, new ExtendedModelMap());

        assertThat(result).isInstanceOf(ResponseEntity.class);
        ResponseEntity<?> response = (ResponseEntity<?>) result;
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        MDC.clear();
    }

    @Test
    void fallsBackToIndexForViewRoutesWithoutAcceptHeader() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getHeader("Accept")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/verify/123");

        ExtendedModelMap model = new ExtendedModelMap();
        Object result = handler.handleBadRequest(new IllegalStateException("oops"), request, model);

        assertThat(result).isEqualTo("index");
        assertThat(model.get("error")).isEqualTo("oops");
    }
}
