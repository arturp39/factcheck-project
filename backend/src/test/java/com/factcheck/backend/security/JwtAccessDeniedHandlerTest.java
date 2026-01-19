package com.factcheck.backend.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAccessDeniedHandlerTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void handle_sendsForbiddenForHtmlRequests() throws IOException, ServletException {
        JwtAccessDeniedHandler handler = new JwtAccessDeniedHandler(new ObjectMapper().findAndRegisterModules());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept", MediaType.TEXT_HTML_VALUE);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("forbidden"));

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void handle_writesJsonForApiRequests() throws IOException, ServletException {
        JwtAccessDeniedHandler handler = new JwtAccessDeniedHandler(new ObjectMapper().findAndRegisterModules());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/claims");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put("corrId", "cid-2");

        handler.handle(request, response, new AccessDeniedException("forbidden"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("Forbidden");
        assertThat(response.getContentAsString()).contains("cid-2");
    }
}
