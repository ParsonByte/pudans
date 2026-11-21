package cn.tolim.pudans.security.authentication.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.security.authentication.oauth2.PudansOAuth2AuthenticationToken;
import cn.tolim.pudans.security.authentication.LoginFlowHandler;
import cn.tolim.pudans.security.authentication.PudansUserDetails;
import cn.tolim.pudans.security.authentication.login.PudansUser;
import cn.tolim.pudans.security.authentication.twofactor.TwoFactorAuthentication;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationSessionTest {

    private static final String REGISTRATION_ID = "feishu";

    @Mock
    ReactiveUserDetailsService reactiveUserDetailsService;

    @Mock
    ServerSecurityContextRepository securityContextRepository;

    @Mock
    LoginFlowHandler loginFlowHandler;

    private OAuth2AuthenticationSession session;

    private ServerWebExchange exchange;

    @BeforeEach
    void setUp() {
        session = new OAuth2AuthenticationSession(
                reactiveUserDetailsService, securityContextRepository, loginFlowHandler);
        exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/login/oauth2/code/feishu").build());
    }

    private static UserDetails user(String username, boolean twoFactorEnabled) {
        var details = new PudansUserDetails(
                username, "encoded", List.of(new SimpleGrantedAuthority("ROLE_authenticated")), false);
        return new PudansUser(details, twoFactorEnabled, twoFactorEnabled ? "encrypted-secret" : null);
    }

    private static OAuth2AuthenticationToken originalToken() {
        var principal = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_authenticated")), Map.of("sub", "1"), "sub");
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), REGISTRATION_ID);
    }

    private AtomicReference<SecurityContext> givenSavedContext() {
        var saved = new AtomicReference<SecurityContext>();
        when(securityContextRepository.save(any(), any())).thenAnswer(invocation -> {
            saved.set(invocation.getArgument(1));
            return Mono.empty();
        });
        return saved;
    }

    private static UserDetails disabledUser(String username) {
        var details = new PudansUserDetails(
                username, "encoded", List.of(new SimpleGrantedAuthority("ROLE_authenticated")), true);
        return new PudansUser(details, false, null);
    }

    @Test
    void disabledAccountCannotEstablishASession() {
        when(reactiveUserDetailsService.findByUsername("bob")).thenReturn(Mono.just(disabledUser("bob")));

        StepVerifier.create(session.establishAndGetAuthentication(exchange, "bob", originalToken(), false))
                .expectError(DisabledException.class)
                .verify();

        verify(securityContextRepository, never()).save(any(), any());
        verify(loginFlowHandler, never()).onLoginSuccess(any(), any());
    }

    @Test
    void unknownAccountCannotEstablishASession() {
        when(reactiveUserDetailsService.findByUsername("ghost")).thenReturn(Mono.empty());

        StepVerifier.create(session.establishAndGetAuthentication(exchange, "ghost", originalToken(), false))
                .expectError(UsernameNotFoundException.class)
                .verify();

        verify(securityContextRepository, never()).save(any(), any());
    }

    @Test
    void twoFactorUserWithAnUnsatisfiedFlagReceivesAChallenge() {
        when(reactiveUserDetailsService.findByUsername("mfa")).thenReturn(Mono.just(user("mfa", true)));
        var saved = givenSavedContext();

        StepVerifier.create(session.establishAndGetAuthentication(exchange, "mfa", originalToken(), false))
                .assertNext(authentication -> assertThat(authentication).isInstanceOf(TwoFactorAuthentication.class))
                .verifyComplete();

        assertThat(saved.get().getAuthentication()).isInstanceOf(TwoFactorAuthentication.class);
        verify(loginFlowHandler, never()).onLoginSuccess(any(), any());
    }

    @Test
    void twoFactorUserWithASatisfiedFlagObtainsTheFullSession() {
        when(reactiveUserDetailsService.findByUsername("mfa")).thenReturn(Mono.just(user("mfa", true)));
        var saved = givenSavedContext();
        when(loginFlowHandler.onLoginSuccess(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(session.establishAndGetAuthentication(exchange, "mfa", originalToken(), true))
                .assertNext(authentication ->
                        assertThat(authentication).isInstanceOf(PudansOAuth2AuthenticationToken.class))
                .verifyComplete();

        assertThat(saved.get().getAuthentication()).isInstanceOf(PudansOAuth2AuthenticationToken.class);
        verify(loginFlowHandler).onLoginSuccess(any(), any());
    }

    @Test
    void userWithoutTwoFactorObtainsTheFullSession() {
        when(reactiveUserDetailsService.findByUsername("alice")).thenReturn(Mono.just(user("alice", false)));
        var saved = givenSavedContext();
        when(loginFlowHandler.onLoginSuccess(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(session.establishAndGetAuthentication(exchange, "alice", originalToken(), false))
                .assertNext(authentication ->
                        assertThat(authentication).isInstanceOf(PudansOAuth2AuthenticationToken.class))
                .verifyComplete();

        assertThat(saved.get().getAuthentication()).isInstanceOf(PudansOAuth2AuthenticationToken.class);
        verify(loginFlowHandler).onLoginSuccess(any(), any());
    }
}
