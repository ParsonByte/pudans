package cn.tolim.pudans.iam.core.auth;

import cn.tolim.pudans.contract.iam.dto.auth.LoginRequest;
import cn.tolim.pudans.contract.iam.dto.auth.LoginResponse;
import cn.tolim.pudans.contract.iam.dto.auth.MfaChallengeRequest;
import cn.tolim.pudans.contract.iam.dto.auth.MfaChallengeResponse;
import cn.tolim.pudans.contract.iam.dto.auth.MfaVerifyRequest;
import cn.tolim.pudans.contract.iam.dto.ticket.UserInfo;
import cn.tolim.pudans.iam.communal.repository.account.UserRepository;
import cn.tolim.pudans.iam.communal.repository.setting.AdministratorRepository;
import cn.tolim.pudans.iam.communal.session.LoginSessionEntity;
import cn.tolim.pudans.iam.core.auth.audit.AuthAuditService;
import cn.tolim.pudans.iam.core.auth.audit.AuthAuditType;
import cn.tolim.pudans.iam.core.auth.exception.InvalidAuthRequestException;
import cn.tolim.pudans.iam.core.auth.mfa.MfaAuthenticator;
import cn.tolim.pudans.iam.core.auth.mfa.MfaChallenge;
import cn.tolim.pudans.iam.core.auth.mfa.MfaChallengeService;
import cn.tolim.pudans.iam.core.auth.mfa.MfaMethod;
import cn.tolim.pudans.iam.core.auth.mfa.exception.MfaChallengeInvalidException;
import cn.tolim.pudans.iam.core.auth.mfa.exception.MfaVerificationException;
import cn.tolim.pudans.iam.core.auth.session.GlobalSessionService;
import cn.tolim.pudans.iam.core.auth.session.SessionTokenGenerator;
import cn.tolim.pudans.iam.core.auth.ticket.TicketTargetValidator;
import cn.tolim.pudans.iam.core.auth.user.ApplicationAccessService;
import cn.tolim.pudans.iam.core.auth.user.UserAccountService;
import cn.tolim.pudans.iam.core.auth.user.UserAccountService.AuthenticatedAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceMfaTests {

    @Mock
    private UserAccountService userAccountService;

    @Mock
    private ApplicationAccessService applicationAccessService;

    @Mock
    private TicketTargetValidator ticketTargetValidator;

    @Mock
    private GlobalSessionService globalSessionService;

    @Mock
    private AuthAuditService authAuditService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AdministratorRepository administratorRepository;

    @Mock
    private ObjectProvider<MfaAuthenticator> mfaAuthenticatorProvider;

    @Mock
    private MfaAuthenticator mfaAuthenticator;

    private MutableClock clock;
    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        MfaChallengeService challengeService = new MfaChallengeService(new IamAuthProperties(),
                new SessionTokenGenerator(), clock);
        service = new AuthenticationService(userAccountService, applicationAccessService,
                ticketTargetValidator, globalSessionService, authAuditService, userRepository,
                administratorRepository, challengeService, mfaAuthenticatorProvider);
    }

    @Test
    void loginWithoutMfaCreatesGlobalSession() {
        when(userAccountService.authenticate("alice", "secret")).thenReturn(account());
        when(mfaAuthenticatorProvider.getIfAvailable()).thenReturn(mfaAuthenticator);
        when(mfaAuthenticator.availableMethods("user-1", "USER")).thenReturn(Set.of());
        LoginSessionEntity session = session();
        when(globalSessionService.createSession("user-1", "USER", null, "127.0.0.1", "JUnit"))
                .thenReturn(session);
        when(userAccountService.loadUserInfo(session)).thenReturn(userInfo());

        LoginResponse response = service.login(loginRequest(), "127.0.0.1", "JUnit");

        assertThat(response.getSid()).isEqualTo("sid-1");
        assertThat(response.getMfaRequired()).isFalse();
        assertThat(response.getMfaChallengeId()).isNull();
        verify(globalSessionService).createSession("user-1", "USER", null, "127.0.0.1", "JUnit");
        verify(authAuditService).record(eq(AuthAuditType.LOGIN_SUCCESS), eq("user-1"), eq("USER"),
                eq(null), eq("sid-1"), eq("127.0.0.1"), eq("JUnit"), eq(true), eq(null));
    }

    @Test
    void loginWithTotpReturnsChallengeWithoutSession() {
        when(userAccountService.authenticate("alice", "secret")).thenReturn(account());
        when(mfaAuthenticatorProvider.getIfAvailable()).thenReturn(mfaAuthenticator);
        when(mfaAuthenticator.availableMethods("user-1", "USER"))
                .thenReturn(Set.of(MfaMethod.TOTP));

        LoginResponse response = service.login(loginRequest(), "127.0.0.1", "JUnit");

        assertThat(response.getMfaRequired()).isTrue();
        assertThat(response.getMfaChallengeId()).isNotBlank();
        assertThat(response.getSid()).isNull();
        assertThat(response.getUser()).isNull();
        verify(globalSessionService, never()).createSession(any(), any(), any(), any(), any());
        verify(authAuditService).record(eq(AuthAuditType.MFA_CHALLENGE_ISSUED), eq("user-1"),
                eq("USER"), eq(null), eq(null), eq("127.0.0.1"), eq("JUnit"), eq(true),
                eq("TOTP"));
    }

    @Test
    void loginWithWebAuthnReturnsChallengeWithoutSession() {
        when(userAccountService.authenticate("alice", "secret")).thenReturn(account());
        when(mfaAuthenticatorProvider.getIfAvailable()).thenReturn(mfaAuthenticator);
        when(mfaAuthenticator.availableMethods("user-1", "USER"))
                .thenReturn(Set.of(MfaMethod.WEBAUTHN));

        LoginResponse response = service.login(loginRequest(), "127.0.0.1", "JUnit");

        assertThat(response.getMfaRequired()).isTrue();
        assertThat(response.getMfaChallengeId()).isNotBlank();
        verify(globalSessionService, never()).createSession(any(), any(), any(), any(), any());
    }

    @Test
    void verifyMfaCompletesGlobalSession() {
        when(userAccountService.authenticate("alice", "secret")).thenReturn(account());
        when(mfaAuthenticatorProvider.getIfAvailable()).thenReturn(mfaAuthenticator);
        when(mfaAuthenticator.availableMethods("user-1", "USER"))
                .thenReturn(Set.of(MfaMethod.TOTP));
        LoginResponse challenge = service.login(loginRequest(), "127.0.0.1", "JUnit");
        when(mfaAuthenticator.verifyTotp("user-1", "USER", "123456")).thenReturn(true);
        LoginSessionEntity session = session();
        when(globalSessionService.createSession("user-1", "USER", null, "127.0.0.1", "JUnit"))
                .thenReturn(session);
        when(userAccountService.loadUserInfo(session)).thenReturn(userInfo());

        LoginResponse response = service.verifyMfa(verifyRequest(challenge.getMfaChallengeId()),
                "127.0.0.1", "JUnit");

        assertThat(response.getSid()).isEqualTo("sid-1");
        assertThat(response.getMfaRequired()).isFalse();
        assertThat(response.getUser()).isNotNull();
        verify(authAuditService).record(eq(AuthAuditType.MFA_VERIFICATION_SUCCESS), eq("user-1"),
                eq("USER"), eq(null), eq("sid-1"), eq("127.0.0.1"), eq("JUnit"), eq(true),
                eq(null));
    }

    @Test
    void verifyMfaConsumesChallengeExactlyOnce() {
        when(userAccountService.authenticate("alice", "secret")).thenReturn(account());
        when(mfaAuthenticatorProvider.getIfAvailable()).thenReturn(mfaAuthenticator);
        when(mfaAuthenticator.availableMethods("user-1", "USER"))
                .thenReturn(Set.of(MfaMethod.TOTP));
        LoginResponse challenge = service.login(loginRequest(), "127.0.0.1", "JUnit");
        when(mfaAuthenticator.verifyTotp("user-1", "USER", "123456")).thenReturn(true);
        LoginSessionEntity session = session();
        when(globalSessionService.createSession("user-1", "USER", null, "127.0.0.1", "JUnit"))
                .thenReturn(session);
        when(userAccountService.loadUserInfo(session)).thenReturn(userInfo());

        service.verifyMfa(verifyRequest(challenge.getMfaChallengeId()), "127.0.0.1", "JUnit");

        assertThatThrownBy(() -> service.verifyMfa(verifyRequest(challenge.getMfaChallengeId()),
                "127.0.0.1", "JUnit")).isInstanceOf(MfaChallengeInvalidException.class);
    }

    @Test
    void verifyMfaRejectsInvalidCode() {
        when(userAccountService.authenticate("alice", "secret")).thenReturn(account());
        when(mfaAuthenticatorProvider.getIfAvailable()).thenReturn(mfaAuthenticator);
        when(mfaAuthenticator.availableMethods("user-1", "USER"))
                .thenReturn(Set.of(MfaMethod.TOTP));
        LoginResponse challenge = service.login(loginRequest(), "127.0.0.1", "JUnit");
        when(mfaAuthenticator.verifyTotp("user-1", "USER", "123456")).thenReturn(false);

        assertThatThrownBy(() -> service.verifyMfa(verifyRequest(challenge.getMfaChallengeId()),
                "127.0.0.1", "JUnit")).isInstanceOf(MfaVerificationException.class);
        verify(globalSessionService, never()).createSession(any(), any(), any(), any(), any());
        verify(authAuditService).record(eq(AuthAuditType.MFA_VERIFICATION_FAILURE), eq("user-1"),
                eq("USER"), eq(null), eq(null), eq("127.0.0.1"), eq("JUnit"), eq(false),
                eq("mfa_code_invalid"));
    }

    @Test
    void verifyMfaRejectsWebAuthnOnlyChallenge() {
        when(userAccountService.authenticate("alice", "secret")).thenReturn(account());
        when(mfaAuthenticatorProvider.getIfAvailable()).thenReturn(mfaAuthenticator);
        when(mfaAuthenticator.availableMethods("user-1", "USER"))
                .thenReturn(Set.of(MfaMethod.WEBAUTHN));
        LoginResponse challenge = service.login(loginRequest(), "127.0.0.1", "JUnit");

        assertThatThrownBy(() -> service.verifyMfa(verifyRequest(challenge.getMfaChallengeId()),
                "127.0.0.1", "JUnit")).isInstanceOf(InvalidAuthRequestException.class)
                .hasMessageContaining("TOTP");
        verify(mfaAuthenticator, never()).verifyTotp(any(), any(), any());
    }

    @Test
    void verifyMfaRejectsUsernameMismatch() {
        when(userAccountService.authenticate("alice", "secret")).thenReturn(account());
        when(mfaAuthenticatorProvider.getIfAvailable()).thenReturn(mfaAuthenticator);
        when(mfaAuthenticator.availableMethods("user-1", "USER"))
                .thenReturn(Set.of(MfaMethod.TOTP));
        LoginResponse challenge = service.login(loginRequest(), "127.0.0.1", "JUnit");
        MfaVerifyRequest request = verifyRequest(challenge.getMfaChallengeId());
        request.setUsername("mallory");

        assertThatThrownBy(() -> service.verifyMfa(request, "127.0.0.1", "JUnit"))
                .isInstanceOf(MfaChallengeInvalidException.class);
        verify(mfaAuthenticator, never()).verifyTotp(any(), any(), any());
    }

    @Test
    void verifyMfaRejectsMismatchedApplication() {
        when(userAccountService.authenticate("alice", "secret")).thenReturn(account());
        when(mfaAuthenticatorProvider.getIfAvailable()).thenReturn(mfaAuthenticator);
        when(mfaAuthenticator.availableMethods("user-1", "USER"))
                .thenReturn(Set.of(MfaMethod.TOTP));
        LoginResponse challenge = service.login(loginRequest(), "127.0.0.1", "JUnit");
        MfaVerifyRequest request = verifyRequest(challenge.getMfaChallengeId());
        request.setAppCode("order");

        assertThatThrownBy(() -> service.verifyMfa(request, "127.0.0.1", "JUnit"))
                .isInstanceOf(MfaChallengeInvalidException.class);
    }

    @Test
    void verifyMfaRejectsExpiredChallenge() {
        when(userAccountService.authenticate("alice", "secret")).thenReturn(account());
        when(mfaAuthenticatorProvider.getIfAvailable()).thenReturn(mfaAuthenticator);
        when(mfaAuthenticator.availableMethods("user-1", "USER"))
                .thenReturn(Set.of(MfaMethod.TOTP));
        LoginResponse challenge = service.login(loginRequest(), "127.0.0.1", "JUnit");

        clock.advance(Duration.ofMinutes(6));

        assertThatThrownBy(() -> service.verifyMfa(verifyRequest(challenge.getMfaChallengeId()),
                "127.0.0.1", "JUnit")).isInstanceOf(MfaChallengeInvalidException.class);
    }

    @Test
    void mfaChallengeReturnsPendingChallengeMetadata() {
        when(userAccountService.authenticate("alice", "secret")).thenReturn(account());
        when(mfaAuthenticatorProvider.getIfAvailable()).thenReturn(mfaAuthenticator);
        when(mfaAuthenticator.availableMethods("user-1", "USER"))
                .thenReturn(new LinkedHashSet<>(List.of(MfaMethod.TOTP, MfaMethod.WEBAUTHN)));
        LoginResponse challenge = service.login(loginRequest(), "127.0.0.1", "JUnit");

        MfaChallengeResponse response = service.mfaChallenge(MfaChallengeRequest.builder()
                .username("alice").build());

        assertThat(response.getChallengeId()).isEqualTo(challenge.getMfaChallengeId());
        assertThat(response.getMethod()).isEqualTo(MfaMethod.TOTP.name());
        assertThat(response.getUsername()).isEqualTo("alice");
        assertThat(response.getExpiresAt()).isEqualTo(LocalDateTime.parse("2026-01-01T00:05:00"));
    }

    @Test
    void requireMfaChallengeReturnsChallengeForWebAuthn() {
        when(userAccountService.authenticate("alice", "secret")).thenReturn(account());
        when(mfaAuthenticatorProvider.getIfAvailable()).thenReturn(mfaAuthenticator);
        when(mfaAuthenticator.availableMethods("user-1", "USER"))
                .thenReturn(Set.of(MfaMethod.WEBAUTHN));
        LoginResponse challenge = service.login(loginRequest(), "127.0.0.1", "JUnit");

        MfaChallenge resolved = service.requireMfaChallenge(challenge.getMfaChallengeId(),
                "alice", null, MfaMethod.WEBAUTHN);

        assertThat(resolved.userId()).isEqualTo("user-1");
        assertThat(resolved.supports(MfaMethod.WEBAUTHN)).isTrue();
    }

    private LoginRequest loginRequest() {
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("secret");
        return request;
    }

    private MfaVerifyRequest verifyRequest(String challengeId) {
        MfaVerifyRequest request = new MfaVerifyRequest();
        request.setChallengeId(challengeId);
        request.setCode("123456");
        return request;
    }

    private static AuthenticatedAccount account() {
        return new AuthenticatedAccount("user-1", "alice", "USER", null, "Alice");
    }

    private static LoginSessionEntity session() {
        LoginSessionEntity session = new LoginSessionEntity();
        session.setSid("sid-1");
        session.setUserId("user-1");
        session.setUserType("USER");
        session.setCreatedAt(LocalDateTime.now());
        session.setLastSeenAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusHours(8));
        session.setRevoked(Boolean.FALSE);
        return session;
    }

    private static UserInfo userInfo() {
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId("user-1");
        userInfo.setUsername("alice");
        userInfo.setUserType("USER");
        return userInfo;
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}