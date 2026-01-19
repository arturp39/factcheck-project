package com.factcheck.backend.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthEntryPointTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void commence_redirectsForHtmlRequests() throws IOException, ServletException {
        JwtAuthEntryPoint entryPoint = new JwtAuthEntryPoint(new ObjectMapper().findAndRegisterModules());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept", MediaType.TEXT_HTML_VALUE);
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("bad"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login");
    }

    @Test
    void commence_writesJsonForApiRequests() throws IOException, ServletException {
        JwtAuthEntryPoint entryPoint = new JwtAuthEntryPoint(new ObjectMapper().findAndRegisterModules());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/claims");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put("corrId", "cid-1");

        entryPoint.commence(request, response, new BadCredentialsException("bad"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("Unauthorized");
        assertThat(response.getContentAsString()).contains("cid-1");
    }
}
