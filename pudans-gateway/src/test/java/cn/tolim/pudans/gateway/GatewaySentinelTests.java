package cn.tolim.pudans.gateway;

import cn.tolim.pudans.communal.core.error.CommonErrorCode;
import com.alibaba.cloud.sentinel.gateway.scg.SentinelSCGAutoConfiguration;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPathPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter;
import com.alibaba.csp.sentinel.adapter.gateway.sc.exception.SentinelGatewayBlockExceptionHandler;
import com.alibaba.csp.sentinel.adapter.spring.webflux.SentinelWebFluxFilter;
import com.alibaba.csp.sentinel.datasource.Converter;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GatewaySentinelTests {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private SentinelGatewayBlockExceptionHandler blockExceptionHandler;

    @Test
    void sentinelGatewayBeansAreRegistered() {
        assertThat(context.getBean(SentinelGatewayFilter.class)).isNotNull();
        assertThat(context.getBean(SentinelGatewayBlockExceptionHandler.class)).isNotNull();
        assertThat(context.getBean(SentinelSCGAutoConfiguration.class)).isNotNull();
    }

    @Test
    void sentinelGatewayFilterUsesConfiguredOrder() {
        assertThat(context.getBean(SentinelGatewayFilter.class).getOrder()).isEqualTo(-2147483647);
    }

    @Test
    void webFluxUrlFilterIsDisabled() {
        assertThat(context.getBeanProvider(SentinelWebFluxFilter.class).getIfAvailable()).isNull();
    }

    @Test
    void servletStackIsNotOnTheClasspath() {
        ClassLoader classLoader = getClass().getClassLoader();
        assertThat(ClassUtils.isPresent("org.springframework.web.servlet.DispatcherServlet", classLoader))
                .isFalse();
        assertThat(ClassUtils.isPresent("org.springframework.web.reactive.DispatcherHandler", classLoader))
                .isTrue();
    }

    @Test
    void blockedRequestsReturnRateLimitedResponse() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/apis/pudans.tolim.cn/v1alpha1/iam/auth/login").build());
        blockExceptionHandler.handle(exchange, new FlowException("flow rule")).block();
        assertThat(exchange.getResponse().getStatusCode().value())
                .isEqualTo(CommonErrorCode.RATE_LIMITED.status());
        assertThat(exchange.getResponse().getHeaders().getContentType()).isNotNull();
        assertThat(exchange.getResponse().getHeaders().getContentType()
                .isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"code\":\"" + CommonErrorCode.RATE_LIMITED.code() + "\"");
    }

    @Test
    void gatewayRuleSeedsAreValid() throws IOException {
        String gatewaySeed = readSeed("pudans-gateway.yaml");
        assertThat(gatewaySeed).contains("data-id: pudans-gateway-sentinel-gw-api-group.json");
        assertThat(gatewaySeed).contains("data-id: pudans-gateway-sentinel-gw-flow.json");
        assertThat(context.containsBean("sentinel-json-gw-api-group-converter")).isTrue();
        assertThat(context.containsBean("sentinel-json-gw-flow-converter")).isTrue();
        List<ApiDefinition> apiGroups = convertSeed("sentinel-json-gw-api-group-converter",
                readSeed("pudans-gateway-sentinel-gw-api-group.json"));
        List<GatewayFlowRule> flowRules = convertSeed("sentinel-json-gw-flow-converter",
                readSeed("pudans-gateway-sentinel-gw-flow.json"));
        assertThat(apiGroups).isNotEmpty();
        assertThat(flowRules).isNotEmpty();
        assertThat(apiGroups).extracting(ApiDefinition::getApiName)
                .contains("pudans-iam-auth-api", "pudans-iam-api");
        assertThat(apiGroups).allSatisfy(definition -> {
            assertThat(definition.getApiName()).isNotBlank();
            assertThat(definition.getPredicateItems()).isNotEmpty();
            assertThat(definition.getPredicateItems()).allSatisfy(item -> {
                assertThat(item).isInstanceOf(ApiPathPredicateItem.class);
                assertThat(((ApiPathPredicateItem) item).getPattern())
                        .startsWith("/apis/pudans.tolim.cn/v1alpha1/");
            });
        });
        assertThat(flowRules).allSatisfy(rule -> {
            assertThat(rule.getResource()).isNotBlank();
            assertThat(rule.getResourceMode()).isEqualTo(1);
            assertThat(rule.getCount()).isPositive();
            assertThat(rule.getIntervalSec()).isPositive();
        });
    }

    private String readSeed(String name) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(name)) {
            assertThat(input).as("missing test resource %s", name).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> convertSeed(String beanName, String json) {
        Converter<String, Collection<Object>> converter = context.getBean(beanName, Converter.class);
        return converter.convert(json).stream().map(item -> (T) item).toList();
    }
}