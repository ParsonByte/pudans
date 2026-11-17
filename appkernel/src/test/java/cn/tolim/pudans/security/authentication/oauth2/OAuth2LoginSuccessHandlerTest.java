package cn.tolim.pudans.security.authentication.oauth2;

import static cn.tolim.pudans.security.authorization.AuthorityUtils.ROLE_PREFIX;
import static cn.tolim.pudans.security.authorization.AuthorityUtils.TWO_FACTOR_AUTHENTICATED_ROLE_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.user.UserConnection;
import cn.tolim.pudans.core.user.service.UserConnectionService;
import cn.tolim.pudans.infra.properties.SecurityProperties;
import cn.tolim.pudans.security.authentication.PudansUserDetails;
import cn.tolim.pudans.security.authentication.login.PudansUser;
import cn.tolim.pudans.security.authentication.twofactor.TwoFactorAuthentication;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandlerTest {

    private static final String REGISTRATION_ID = "feishu";

    private static final String USERNAME = "alice";

    @Mock
    UserConnectionService connectionService;

    @Mock
    OAuth2AuthenticationSession authenticationSession;

    @Mock
    ServerResponse.Context context;

    private OAuth2LoginSuccessHandler handler;

    private ServerWebExchange exchange;

    @BeforeEach
    void setUp() {
        handler = new OAuth2LoginSuccessHandler(
                connectionService, authenticationSession, new SecurityProperties(), context);
        exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/login/oauth2/code/feishu").build());
    }

    private static OAuth2AuthenticationToken oauth2Token() {
        var principal = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_authenticated")), Map.of("sub", "1"), "sub");
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), REGISTRATION_ID);
    }

    private static Authentication previousAuthentication(boolean withFactor) {
        var details = new PudansUserDetails(
                USERNAME, "encoded", List.of(new SimpleGrantedAuthority("ROLE_authenticated")), false);
        var user = new PudansUser(details, true, "encrypted-secret");
        var authorities = withFactor
                ? List.of(
                        new SimpleGrantedAuthority("ROLE_authenticated"),
                        new SimpleGrantedAuthority(ROLE_PREFIX + TWO_FACTOR_AUTHENTICATED_ROLE_NAME))
                : List.of(new SimpleGrantedAuthority("ROLE_authenticated"));
        return UsernamePasswordAuthenticationToken.authenticated(user, "", authorities);
    }

    private void givenBoundConnection() {
        var connection = new UserConnection();
        var spec = new UserConnection.UserConnectionSpec();
        spec.setUsername(USERNAME);
        connection.setSpec(spec);
        when(connectionService.updateUserConnectionIfPresent(eq(REGISTRATION_ID), any()))
                .thenReturn(Mono.just(connection));
        when(authenticationSession.establishAndGetAuthentication(any(), eq(USERNAME), any(), anyBoolean()))
                .thenReturn(Mono.empty());
    }

    private boolean establishedTwoFactorSatisfiedFlag() {
        StepVerifier.create(handler.onAuthenticationSuccess(
                        new WebFilterExchange(exchange, request -> Mono.empty()), oauth2Token()))
                .verifyComplete();
        var captor = ArgumentCaptor.forClass(Boolean.class);
        verify(authenticationSession)
                .establishAndGetAuthentication(any(), eq(USERNAME), any(), captor.capture());
        return Boolean.TRUE.equals(captor.getValue());
    }

    @Test
    void freshLoginWithoutPreviousAuthenticationIsNotTwoFactorSatisfied() {
        givenBoundConnection();

        assertThat(establishedTwoFactorSatisfiedFlag()).isFalse();
    }

    @Test
    void previousAuthenticationThatPassedTwoFactorIsSatisfied() {
        givenBoundConnection();
        exchange.getAttributes()
                .put(OAuth2LoginAttributes.PRE_AUTHENTICATION, previousAuthentication(true));

        assertThat(establishedTwoFactorSatisfiedFlag()).isTrue();
    }

    @Test
    void pendingPreviousAuthenticationIsNotSatisfied() {
        givenBoundConnection();
        exchange.getAttributes()
                .put(
                        OAuth2LoginAttributes.PRE_AUTHENTICATION,
                        new TwoFactorAuthentication(previousAuthentication(false)));

        assertThat(establishedTwoFactorSatisfiedFlag()).isFalse();
    }
}
