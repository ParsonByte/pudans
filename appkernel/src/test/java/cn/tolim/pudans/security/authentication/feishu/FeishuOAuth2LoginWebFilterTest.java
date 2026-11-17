package cn.tolim.pudans.security.authentication.feishu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.HttpStatus.FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.core.event.DomainEventPublisher;
import cn.tolim.pudans.core.user.service.FeishuUserService;
import cn.tolim.pudans.infra.feishu.FeishuClient;
import cn.tolim.pudans.infra.properties.FeishuProperties;
import cn.tolim.pudans.infra.properties.SecurityProperties;
import cn.tolim.pudans.security.authentication.ExternalLoginCompletionService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class FeishuOAuth2LoginWebFilterTest {

    @Mock
    FeishuClient feishuClient;

    @Mock
    FeishuUserService feishuUserService;

    @Mock
    ExternalLoginCompletionService externalLoginCompletionService;

    @Mock
    DomainEventPublisher domainEventPublisher;

    private FeishuProperties properties;

    private FeishuOAuth2LoginWebFilter filter;

    @BeforeEach
    void setUp() {
        properties = new FeishuProperties();
        var context = mock(ServerResponse.Context.class);
        lenient().when(context.messageWriters()).thenReturn(ServerCodecConfigurer.create().getWriters());
        lenient().when(context.viewResolvers()).thenReturn(List.of());
        filter = new FeishuOAuth2LoginWebFilter(
                properties,
                feishuClient,
                feishuUserService,
                externalLoginCompletionService,
                domainEventPublisher,
                new SecurityProperties(),
                context);
    }

    private static MockServerWebExchange authorizationExchange() {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get(ApiGroups.API_PREFIX + "/oauth2/authorization/feishu").build());
    }

    private static WebFilterChain chain() {
        return ignored -> Mono.empty();
    }

    @Test
    void enabledWithoutCredentialsReturnsNotConfigured() {
        properties.setEnabled(true);

        var exchange = authorizationExchange();

        StepVerifier.create(filter.filter(exchange, chain())).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("feishu_not_configured");
        verifyNoInteractions(feishuClient, feishuUserService, externalLoginCompletionService);
    }

    @Test
    void configuredFeishuRedirectsToTheAuthorizationEndpoint() {
        properties.setEnabled(true);
        properties.setAppId("app-id");
        properties.setAppSecret("app-secret");

        var exchange = authorizationExchange();

        StepVerifier.create(filter.filter(exchange, chain())).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(FOUND);
        var location = exchange.getResponse().getHeaders().getLocation();
        assertThat(location).isNotNull();
        assertThat(location.toString()).contains("app_id=app-id");
        verifyNoInteractions(feishuClient, feishuUserService, externalLoginCompletionService);
    }
}
