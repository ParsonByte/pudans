package cn.tolim.pudans.core.audit.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.audit.AuditLog;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class UserNavigationEndpointTest {

    private final UserNavigationEndpoint endpoint = new UserNavigationEndpoint();

    @Test
    void navigationIsRecordedAsPageNavigation() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/usernavigations").build());
        exchange.getAttributes().put(AuditLog.AUDIT_ID_ATTRIBUTE, "audit-1");
        exchange.getAttributes().put(AuditLog.TRACE_FLOW_ID_ATTRIBUTE, "flow-1");
        var request = mock(ServerRequest.class);
        when(request.exchange()).thenReturn(exchange);
        when(request.bodyToMono(UserNavigationEndpoint.NavigationEvent.class))
                .thenReturn(Mono.just(new UserNavigationEndpoint.NavigationEvent("/console/roles")));

        StepVerifier.create(endpoint.record(request))
                .assertNext(response -> assertThat(response.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();

        assertThat(exchange.getAttributes().get(AuditLog.OPERATION_ATTRIBUTE))
                .isEqualTo("NAVIGATION");
        assertThat(exchange.getAttributes().get(AuditLog.RESOURCE_TYPE_ATTRIBUTE))
                .isEqualTo("Page");
        assertThat(exchange.getAttributes().get(AuditLog.RESOURCE_NAME_ATTRIBUTE))
                .isEqualTo("/console/roles");
        assertThat(exchange.getAttributes().get(AuditLog.RESULT_ATTRIBUTE))
                .isEqualTo("SUCCESS");
    }

    @Test
    void invalidNavigationPathIsRejected() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/usernavigations").build());
        var request = mock(ServerRequest.class);
        when(request.exchange()).thenReturn(exchange);
        when(request.bodyToMono(UserNavigationEndpoint.NavigationEvent.class))
                .thenReturn(Mono.just(new UserNavigationEndpoint.NavigationEvent("https://evil.test")));

        StepVerifier.create(endpoint.record(request))
                .expectError(ServerWebInputException.class)
                .verify();
    }
}
