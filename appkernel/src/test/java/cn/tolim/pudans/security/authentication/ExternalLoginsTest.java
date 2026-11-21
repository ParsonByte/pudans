package cn.tolim.pudans.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.infra.event.DomainEventPublisher;
import cn.tolim.pudans.security.authentication.login.PudansUser;
import cn.tolim.pudans.security.authentication.twofactor.TwoFactorAuthentication;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ExternalLoginsTest {

    @Mock
    ReactiveUserDetailsService reactiveUserDetailsService;

    @Mock
    ServerSecurityContextRepository securityContextRepository;

    @Mock
    LoginFlowHandler loginFlowHandler;

    @Mock
    DomainEventPublisher domainEventPublisher;

    private ExternalLogins service;

    private ServerWebExchange exchange;

    @BeforeEach
    void setUp() {
        service = new ExternalLogins(
                reactiveUserDetailsService, securityContextRepository, loginFlowHandler, domainEventPublisher);
        exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/login").build());
    }

    private static UserDetails user(String username, boolean disabled, boolean twoFactorEnabled) {
        var details = new PudansUserDetails(
                username,
                "encoded",
                List.of(new SimpleGrantedAuthority("ROLE_authenticated")),
                disabled);
        return new PudansUser.Builder(details)
                .twoFactorAuthEnabled(twoFactorEnabled)
                .totpEncryptedSecret(twoFactorEnabled ? "encrypted-secret" : null)
                .build();
    }

    @Test
    void disabledUserIsRejectedBeforeAnySessionIsCreated() {
        when(reactiveUserDetailsService.findByUsername("disabled"))
                .thenReturn(Mono.just(user("disabled", true, false)));

        StepVerifier.create(service.complete(exchange, "disabled", "passkey"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(LoginRejectedException.class);
                    assertThat(((LoginRejectedException) error).getReason()).isEqualTo("account_disabled");
                })
                .verify();

        verify(securityContextRepository, never()).save(any(), any());
        verify(loginFlowHandler, never()).onLoginSuccess(any(), any());
        verify(domainEventPublisher)
                .publish(eq("user.login.failed"), any(), eq(null));
        assertThat(exchange.getAttributes())
                .containsEntry(AuditLog.RESULT_ATTRIBUTE, "DENIED")
                .containsEntry(AuditLog.CONTEXT_ATTRIBUTE, "account_disabled");
    }

    @Test
    void twoFactorUserReceivesAChallengeInsteadOfAFullSession() {
        var savedContext = new AtomicReference<SecurityContext>();
        when(reactiveUserDetailsService.findByUsername("mfa")).thenReturn(Mono.just(user("mfa", false, true)));
        when(securityContextRepository.save(any(), any())).thenAnswer(invocation -> {
            savedContext.set(invocation.getArgument(1));
            return Mono.empty();
        });

        StepVerifier.create(service.complete(exchange, "mfa", "passkey"))
                .assertNext(result -> {
                    assertThat(result.twoFactorRequired()).isTrue();
                    assertThat(result.username()).isEqualTo("mfa");
                })
                .verifyComplete();

        assertThat(savedContext.get()).isNotNull();
        assertThat(savedContext.get().getAuthentication()).isInstanceOf(TwoFactorAuthentication.class);
        verify(loginFlowHandler, never()).onLoginSuccess(any(), any());
    }

    @Test
    void normalUserCompletesTheLoginFlow() {
        var savedContext = new AtomicReference<SecurityContext>();
        when(reactiveUserDetailsService.findByUsername("alice")).thenReturn(Mono.just(user("alice", false, false)));
        when(securityContextRepository.save(any(), any())).thenAnswer(invocation -> {
            savedContext.set(invocation.getArgument(1));
            return Mono.empty();
        });
        when(loginFlowHandler.onLoginSuccess(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(service.complete(exchange, "alice", "feishu"))
                .assertNext(result -> {
                    assertThat(result.twoFactorRequired()).isFalse();
                    assertThat(result.username()).isEqualTo("alice");
                })
                .verifyComplete();

        assertThat(savedContext.get()).isNotNull();
        assertThat(savedContext.get().getAuthentication())
                .isInstanceOf(UsernamePasswordAuthenticationToken.class);
        verify(loginFlowHandler).onLoginSuccess(any(), any());
    }
}
