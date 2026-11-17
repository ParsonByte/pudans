package cn.tolim.pudans.security.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.audit.service.AuditService;

@ExtendWith(MockitoExtension.class)
class AuditWebFilterTest {

    @Mock
    AuditService auditService;

    @Mock
    ReactiveExtensionClient client;

    @Mock
    AuditRecordRuleEvaluator ruleEvaluator;

    private AuditWebFilter filter;

    @BeforeEach
    void setUp() {
        lenient()
                .when(ruleEvaluator.shouldRecord(anyString(), anyString()))
                .thenReturn(Mono.just(true));
        filter = new AuditWebFilter(auditService, client, ruleEvaluator);
    }

    private static WebFilterChain chain() {
        return exchange -> Mono.empty();
    }

    @Test
    void runsBeforeTheSecurityFilterChain() {
        var order = AuditWebFilter.class.getAnnotation(org.springframework.core.annotation.Order.class);

        assertThat(order.value()).isLessThan(-100);
    }

    @Test
    void upstreamFlowIdIsNotOverwritten() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users").build());
        exchange.getAttributes().put(AuditService.TRACE_FLOW_ID_ATTRIBUTE, "flow-from-risk-filter");

        StepVerifier.create(filter.filter(exchange, chain())).verifyComplete();

        assertThat(exchange.getAttributes().get(AuditService.TRACE_FLOW_ID_ATTRIBUTE))
                .isEqualTo("flow-from-risk-filter");
        verify(auditService)
                .record(same(exchange), anyString(), anyString(), any(), anyString(), anyLong(), anyMap());
    }

    @Test
    void inheritedHeaderIsUsedWhenNoFlowIdExistsYet() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users")
                .header(AuditService.TRACE_FLOW_ID_HEADER, "flow-from-header")
                .build());

        StepVerifier.create(filter.filter(exchange, chain())).verifyComplete();

        assertThat(exchange.getAttributes().get(AuditService.TRACE_FLOW_ID_ATTRIBUTE))
                .isEqualTo("flow-from-header");
    }

    @Test
    void oversizedHeaderIsTruncatedBeforeItIsStored() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users")
                .header(AuditService.TRACE_FLOW_ID_HEADER, "x".repeat(500))
                .build());

        StepVerifier.create(filter.filter(exchange, chain())).verifyComplete();

        assertThat((String) exchange.getAttributes().get(AuditService.TRACE_FLOW_ID_ATTRIBUTE))
                .hasSize(64);
    }

    @Test
    void rulesCanSuppressTheAuditRecord() {
        when(ruleEvaluator.shouldRecord(anyString(), anyString())).thenReturn(Mono.just(false));
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users").build());

        StepVerifier.create(filter.filter(exchange, chain())).verifyComplete();

        verifyNoInteractions(auditService);
    }

    @Test
    void staticResourcesAreNotAudited() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/webjars/app.js").build());

        StepVerifier.create(filter.filter(exchange, chain())).verifyComplete();

        verifyNoInteractions(auditService);
    }
}
