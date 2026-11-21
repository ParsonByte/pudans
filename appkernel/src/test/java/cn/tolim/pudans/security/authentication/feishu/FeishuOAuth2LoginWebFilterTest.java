package cn.tolim.pudans.security.authentication.feishu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import cn.tolim.pudans.contract.user.resource.UserConnection;
import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.infra.event.DomainEventPublisher;
import cn.tolim.pudans.core.user.FeishuAccounts;
import cn.tolim.pudans.infra.feishu.FeishuClient;
import cn.tolim.pudans.infra.properties.FeishuProperties;
import cn.tolim.pudans.infra.properties.OidcProperties;
import cn.tolim.pudans.infra.properties.SecurityProperties;
import cn.tolim.pudans.infra.setting.ParameterSettingFetcher;
import cn.tolim.pudans.infra.setting.ParameterSettingSecretCipher;
import cn.tolim.pudans.infra.setting.SecurityParameters;
import cn.tolim.pudans.security.authentication.ExternalLogins;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class FeishuOAuth2LoginWebFilterTest {

    @Mock
    FeishuClient feishuClient;

    @Mock
    FeishuAccounts feishuAccounts;

    @Mock
    ExternalLogins externalLogins;

    @Mock
    DomainEventPublisher domainEventPublisher;

    @Mock
    ParameterSettingFetcher parameterSettingFetcher;

    @Mock
    ParameterSettingSecretCipher parameterSettingSecretCipher;

    private FeishuProperties properties;

    private FeishuOAuth2LoginWebFilter filter;

    @BeforeEach
    void setUp() {
        properties = new FeishuProperties();
        var securitySettings = new SecurityParameters(
                parameterSettingFetcher,
                parameterSettingSecretCipher,
                new OidcProperties(),
                properties);
        var context = mock(ServerResponse.Context.class);
        lenient().when(context.messageWriters()).thenReturn(ServerCodecConfigurer.create().getWriters());
        lenient().when(context.viewResolvers()).thenReturn(List.of());
        filter = new FeishuOAuth2LoginWebFilter(
                securitySettings,
                feishuClient,
                feishuAccounts,
                externalLogins,
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
        verifyNoInteractions(feishuClient, feishuAccounts, externalLogins);
    }

    @Test
    void enabledWithoutCredentialsRedirectsBrowserNavigationToSignIn() {
        properties.setEnabled(true);

        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(ApiGroups.API_PREFIX + "/oauth2/authorization/feishu")
                        .header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE)
                        .build());

        StepVerifier.create(filter.filter(exchange, chain())).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(FOUND);
        var location = exchange.getResponse().getHeaders().getLocation();
        assertThat(location).isNotNull();
        assertThat(location.getPath()).isEqualTo("/sign-in");
        assertThat(location.getQuery()).contains("code=feishu_not_configured");
        verifyNoInteractions(feishuClient, feishuAccounts, externalLogins);
    }

    @Test
    void bindWithoutAuthenticationRedirectsToSignIn() {
        properties.setEnabled(true);
        properties.setAppId("app-id");
        properties.setAppSecret("app-secret");

        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(ApiGroups.API_PREFIX + "/oauth2/authorization/feishu")
                        .queryParam("bind", "true")
                        .header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE)
                        .build());

        StepVerifier.create(filter.filter(exchange, chain())).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(FOUND);
        var location = exchange.getResponse().getHeaders().getLocation();
        assertThat(location).isNotNull();
        assertThat(location.getPath()).isEqualTo("/sign-in");
        assertThat(location.getQuery()).contains("code=feishu_bind_login_required");
    }

    @Test
    void successfulLoginCallbackRedirectsWithoutWritingASecondResponse() {
        properties.setEnabled(true);
        properties.setAppId("app-id");
        properties.setAppSecret("app-secret");
        var exchange = callbackExchange("state-1");
        exchange.getSession()
                .doOnNext(session -> session.getAttributes().put(FeishuOAuth2LoginWebFilter.STATE_ATTRIBUTE, "state-1"))
                .block();
        lenient().when(feishuClient.exchangeCode("code-1"))
                .thenReturn(Mono.just(new FeishuClient.FeishuToken("token-1", 7200)));
        lenient().when(feishuClient.fetchUserInfo("token-1"))
                .thenReturn(Mono.just(new FeishuClient.FeishuUserInfo(
                        "open-1", "union-1", "Alice", "alice@example.com", null, null)));
        var connection = new UserConnection();
        var metadata = new Metadata();
        metadata.setName("alice-feishu");
        connection.setMetadata(metadata);
        var spec = new UserConnection.UserConnectionSpec();
        spec.setRegistrationId("feishu");
        spec.setUsername("alice");
        spec.setProviderUserId("union-1");
        connection.setSpec(spec);
        when(feishuAccounts.findConnection("union-1")).thenReturn(Mono.just(connection));
        when(feishuAccounts.syncProfile("alice", "Alice", "alice@example.com", null, null))
                .thenReturn(Mono.empty());
        when(externalLogins.complete(exchange, "alice", "feishu"))
                .thenReturn(Mono.just(new ExternalLogins.LoginCompletionResult(false, "alice")));

        StepVerifier.create(filter.filter(exchange, chain())).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(FOUND);
        assertThat(exchange.getResponse().getHeaders().getLocation()).hasToString("/");
    }

    @Test
    void bindCallbackCreatesTheConnectionAndRedirectsToTheIdentityPage() {
        properties.setEnabled(true);
        properties.setAppId("app-id");
        properties.setAppSecret("app-secret");
        var exchange = callbackExchange("state-1");
        exchange.getSession()
                .doOnNext(session -> {
                    session.getAttributes().put(FeishuOAuth2LoginWebFilter.STATE_ATTRIBUTE, "state-1");
                    session.getAttributes().put(FeishuOAuth2LoginWebFilter.BIND_ATTRIBUTE, true);
                })
                .block();
        lenient().when(feishuClient.exchangeCode("code-1"))
                .thenReturn(Mono.just(new FeishuClient.FeishuToken("token-1", 7200)));
        lenient().when(feishuClient.fetchUserInfo("token-1"))
                .thenReturn(Mono.just(new FeishuClient.FeishuUserInfo(
                        "open-1", "union-1", "Alice", "alice@example.com", null, null)));
        when(feishuAccounts.bindConnection("alice", "union-1", "Alice", "alice@example.com", null, null))
                .thenReturn(Mono.just(new UserConnection()));
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "alice", "n/a", List.of(new SimpleGrantedAuthority("ROLE_authenticated")));

        StepVerifier.create(filter
                        .filter(exchange, chain())
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(FOUND);
        assertThat(exchange.getResponse().getHeaders().getLocation())
                .hasToString("/identity-providers?bound=feishu");
    }

    private static MockServerWebExchange callbackExchange(String state) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(
                        ApiGroups.API_PREFIX + "/oauth2/callback/feishu")
                .queryParam("code", "code-1")
                .queryParam("state", state)
                .build());
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
        verifyNoInteractions(feishuClient, feishuAccounts, externalLogins);
    }
}
