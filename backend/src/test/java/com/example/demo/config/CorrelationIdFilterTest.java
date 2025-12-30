package com.example.demo.config;

import jakarta.servlet.ServletException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void generatesCorrelationIdWhenMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String headerValue = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        Assertions.assertThat(headerValue).isNotBlank();
        Assertions.assertThat(MDC.get("corrId")).isNull();
    }

    @Test
    void reusesExistingCorrelationId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        Assertions.assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER))
                .isEqualTo("abc-123");
        Assertions.assertThat(MDC.get("corrId")).isNull();
    }
}
