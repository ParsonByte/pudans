package cn.tolim.pudans.communal.log;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingFilterTests {

    @Test
    void skipsDefaultIgnoredPaths() {
        RequestLoggingFilter filter = new RequestLoggingFilter(new PudansLogProperties());

        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/actuator/health"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/orders"))).isFalse();
    }

    @Test
    void honorsCustomIgnoredPaths() {
        PudansLogProperties properties = new PudansLogProperties();
        properties.setIgnoredPaths(List.of("/internal/**"));
        RequestLoggingFilter filter = new RequestLoggingFilter(properties);

        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/internal/ping"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/actuator/health"))).isFalse();
    }

    @Test
    void passesRequestThroughAndKeepsResponseStatus() throws Exception {
        RequestLoggingFilter filter = new RequestLoggingFilter(new PudansLogProperties());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();
        FilterChain chain = (servletRequest, servletResponse) -> {
            invoked.set(true);
            response.setStatus(201);
        };

        filter.doFilter(request, response, chain);

        assertThat(invoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(201);
    }
}