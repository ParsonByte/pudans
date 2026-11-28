package cn.tolim.pudans.iam.core.auth.session;

import cn.tolim.pudans.iam.communal.session.AppSessionEntity;
import cn.tolim.pudans.iam.communal.session.AppSessionRepository;
import cn.tolim.pudans.iam.communal.session.LoginSessionEntity;
import cn.tolim.pudans.iam.communal.session.LoginSessionRepository;
import cn.tolim.pudans.iam.core.auth.IamAuthProperties;
import cn.tolim.pudans.iam.core.auth.exception.SessionNotActiveException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalSessionServiceTests {

    @Mock
    private LoginSessionRepository loginSessionRepository;

    @Mock
    private AppSessionRepository appSessionRepository;

    @Mock
    private SessionCache sessionCache;

    @Mock
    private BackchannelLogoutNotifier backchannelLogoutNotifier;

    private GlobalSessionService service;

    @BeforeEach
    void setUp() {
        IamAuthProperties properties = new IamAuthProperties();
        service = new GlobalSessionService(loginSessionRepository, appSessionRepository, sessionCache,
                backchannelLogoutNotifier, new SessionTokenGenerator(), properties);
    }

    @Test
    void createSessionPersistsAndCaches() {
        when(loginSessionRepository.save(any(LoginSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        LoginSessionEntity session = service.createSession("user-1", "USER", "org-1", "127.0.0.1",
                "JUnit");
        assertThat(session.getSid()).isNotBlank();
        assertThat(session.getUserId()).isEqualTo("user-1");
        assertThat(session.getUserType()).isEqualTo("USER");
        assertThat(session.getOrgId()).isEqualTo("org-1");
        assertThat(session.getExpiresAt()).isAfter(LocalDateTime.now());
        assertThat(session.getRevoked()).isFalse();
        verify(sessionCache).put(eq(session.getSid()), any());
    }

    @Test
    void findActiveReturnsActiveSession() {
        LoginSessionEntity session = activeSession();
        when(loginSessionRepository.findById("sid-1")).thenReturn(Optional.of(session));
        assertThat(service.findActive("sid-1")).contains(session);
        verify(sessionCache).put(eq("sid-1"), any());
    }

    @Test
    void findActiveRejectsRevokedSession() {
        LoginSessionEntity session = activeSession();
        session.setRevoked(Boolean.TRUE);
        when(loginSessionRepository.findById("sid-1")).thenReturn(Optional.of(session));
        assertThat(service.findActive("sid-1")).isEmpty();
        verify(sessionCache).evict("sid-1");
    }

    @Test
    void findActiveRejectsExpiredSession() {
        LoginSessionEntity session = activeSession();
        session.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(loginSessionRepository.findById("sid-1")).thenReturn(Optional.of(session));
        assertThat(service.findActive("sid-1")).isEmpty();
    }

    @Test
    void requireActiveThrowsWhenMissing() {
        when(loginSessionRepository.findById(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.requireActive("missing"))
                .isInstanceOf(SessionNotActiveException.class);
    }

    @Test
    void revokeMarksSessionAndDispatchesBackchannel() {
        LoginSessionEntity session = activeSession();
        AppSessionEntity appSession = new AppSessionEntity();
        appSession.setSid("sid-1");
        appSession.setAppCode("app-a");
        appSession.setUserId("user-1");
        appSession.setExpiresAt(LocalDateTime.now().plusHours(1));
        appSession.setRevoked(Boolean.FALSE);
        when(loginSessionRepository.findById("sid-1")).thenReturn(Optional.of(session));
        when(loginSessionRepository.revokeBySid("sid-1", "logout")).thenReturn(1);
        when(appSessionRepository.findAllBySid("sid-1")).thenReturn(List.of(appSession));
        when(backchannelLogoutNotifier.notifyLogout("sid-1", "user-1", "logout",
                List.of(appSession))).thenReturn(1);

        GlobalSessionService.SessionRevocationResult result = service.revoke("sid-1", "logout");

        assertThat(result.revokedSessionCount()).isEqualTo(1);
        assertThat(result.notifiedApplicationCount()).isEqualTo(1);
        verify(loginSessionRepository).revokeBySid("sid-1", "logout");
        verify(appSessionRepository).revokeAllBySid("sid-1");
        verify(sessionCache).evict("sid-1");
    }

    @Test
    void revokeUnknownSessionIsIdempotent() {
        when(loginSessionRepository.findById("missing")).thenReturn(Optional.empty());
        GlobalSessionService.SessionRevocationResult result = service.revoke("missing", "logout");
        assertThat(result.revokedSessionCount()).isZero();
        verify(appSessionRepository, never()).revokeAllBySid(anyString());
    }

    @Test
    void revokeAllForUserRevokesEveryActiveSession() {
        LoginSessionEntity first = activeSession();
        LoginSessionEntity second = activeSession();
        second.setSid("sid-2");
        when(loginSessionRepository.findAllByUserIdAndRevokedFalse("user-1"))
                .thenReturn(List.of(first, second));
        when(loginSessionRepository.revokeAllByUserId("user-1", "password_changed")).thenReturn(2);
        when(appSessionRepository.findAllBySid(anyString())).thenReturn(List.of());
        when(backchannelLogoutNotifier.notifyLogout(anyString(), eq("user-1"),
                eq("password_changed"), any())).thenReturn(0);

        GlobalSessionService.SessionRevocationResult result = service
                .revokeAllForUser("user-1", "password_changed");

        assertThat(result.revokedSessionCount()).isEqualTo(2);
        verify(sessionCache).evict("sid-1");
        verify(sessionCache).evict("sid-2");
    }

    @Test
    void applicationSessionTtlIsClampedToGlobalSessionTtl() {
        GlobalSessionService clamped = serviceWithTtl(Duration.ofHours(1), Duration.ofHours(12));
        when(appSessionRepository.findByAppCodeAndSid("app-a", "sid-1"))
                .thenReturn(Optional.empty());
        when(appSessionRepository.save(any(AppSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime before = LocalDateTime.now();
        AppSessionEntity saved = clamped.registerApplicationSession("sid-1", "app-1", "app-a",
                "user-1", null);

        assertThat(saved.getExpiresAt()).isBeforeOrEqualTo(before.plusHours(1).plusSeconds(1));
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now().plusMinutes(50));
    }

    @Test
    void applicationSessionTtlUsesEarlierGlobalExpiryWhenProvided() {
        GlobalSessionService clamped = serviceWithTtl(Duration.ofHours(8), Duration.ofHours(8));
        when(appSessionRepository.findByAppCodeAndSid("app-a", "sid-1"))
                .thenReturn(Optional.empty());
        when(appSessionRepository.save(any(AppSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        LocalDateTime sessionExpiresAt = LocalDateTime.now().plusMinutes(10);

        AppSessionEntity saved = clamped.registerApplicationSession("sid-1", "app-1", "app-a",
                "user-1", sessionExpiresAt);

        assertThat(saved.getExpiresAt()).isBeforeOrEqualTo(sessionExpiresAt.plusSeconds(1));
    }

    @Test
    void applicationSessionTtlStaysShorterThanGlobalTtl() {
        GlobalSessionService clamped = serviceWithTtl(Duration.ofHours(8), Duration.ofMinutes(30));
        when(appSessionRepository.findByAppCodeAndSid("app-a", "sid-1"))
                .thenReturn(Optional.empty());
        when(appSessionRepository.save(any(AppSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime before = LocalDateTime.now();
        AppSessionEntity saved = clamped.registerApplicationSession("sid-1", "app-1", "app-a",
                "user-1", null);

        assertThat(saved.getExpiresAt()).isBeforeOrEqualTo(before.plusMinutes(30).plusSeconds(1));
    }

    private GlobalSessionService serviceWithTtl(Duration ttl, Duration appSessionTtl) {
        IamAuthProperties properties = new IamAuthProperties();
        properties.getSession().setTtl(ttl);
        properties.getSession().setAppSessionTtl(appSessionTtl);
        return new GlobalSessionService(loginSessionRepository, appSessionRepository, sessionCache,
                backchannelLogoutNotifier, new SessionTokenGenerator(), properties);
    }

    private LoginSessionEntity activeSession() {
        LoginSessionEntity session = new LoginSessionEntity();
        session.setSid("sid-1");
        session.setUserId("user-1");
        session.setUserType("USER");
        session.setOrgId("org-1");
        session.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        session.setLastSeenAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusHours(1));
        session.setRevoked(Boolean.FALSE);
        return session;
    }
}