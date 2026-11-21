package cn.tolim.pudans.core.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.security.PersonalAccessToken;
import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.core.user.PersonalTokens;
import cn.tolim.pudans.core.infra.setting.SecurityParameters;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ServerWebInputException;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class UserScopedPatHandlerImplTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    PersonalTokens personalTokens;

    @Mock
    AuditLog auditLog;

    @Mock
    SecurityParameters securitySettings;

    private UserScopedPatHandlerImpl handler;

    @BeforeEach
    void setUp() {
        handler = new UserScopedPatHandlerImpl(client, personalTokens, auditLog, securitySettings);
    }

    @Test
    void rejectsCreationWhenPatIsDisabled() {
        when(securitySettings.isPatEnabled()).thenReturn(false);

        StepVerifier.create(handler.validatePolicy(new PersonalAccessToken()))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ServerWebInputException.class)
                        .hasMessageContaining("禁用"))
                .verify();
    }

    @Test
    void rejectsExpiryBeyondTheConfiguredLifetime() {
        when(securitySettings.isPatEnabled()).thenReturn(true);
        when(securitySettings.patMaxLifetimeDays()).thenReturn(7);
        var pat = new PersonalAccessToken();
        pat.getSpec().setExpiresAt(Instant.now().plus(8, ChronoUnit.DAYS));

        StepVerifier.create(handler.validatePolicy(pat))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ServerWebInputException.class)
                        .hasMessageContaining("7"))
                .verify();
    }

    @Test
    void rejectsMissingExpiryWhenALifetimeLimitIsConfigured() {
        when(securitySettings.isPatEnabled()).thenReturn(true);
        when(securitySettings.patMaxLifetimeDays()).thenReturn(7);

        StepVerifier.create(handler.validatePolicy(new PersonalAccessToken()))
                .expectError(ServerWebInputException.class)
                .verify();
    }

    @Test
    void acceptsExpiryWithinTheConfiguredLifetime() {
        when(securitySettings.isPatEnabled()).thenReturn(true);
        when(securitySettings.patMaxLifetimeDays()).thenReturn(7);
        var pat = new PersonalAccessToken();
        pat.getSpec().setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));

        StepVerifier.create(handler.validatePolicy(pat)).expectNext(pat).verifyComplete();
    }
}
