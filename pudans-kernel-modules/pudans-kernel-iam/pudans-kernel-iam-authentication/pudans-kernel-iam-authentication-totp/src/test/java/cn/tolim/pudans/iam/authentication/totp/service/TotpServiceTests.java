package cn.tolim.pudans.iam.authentication.totp.service;

import cn.tolim.pudans.iam.authentication.totp.algorithm.Base32Codec;
import cn.tolim.pudans.iam.authentication.totp.algorithm.TotpCodeGenerator;
import cn.tolim.pudans.iam.authentication.totp.algorithm.TotpCodeVerifier;
import cn.tolim.pudans.iam.authentication.totp.config.TotpAuthenticationProperties;
import cn.tolim.pudans.iam.authentication.totp.crypto.SpissatusTotpSecretProtector;
import cn.tolim.pudans.iam.authentication.totp.dto.TotpEnrollResponse;
import cn.tolim.pudans.iam.authentication.totp.entity.TotpCredentialEntity;
import cn.tolim.pudans.iam.authentication.totp.enums.TotpAlgorithm;
import cn.tolim.pudans.iam.authentication.totp.enums.TotpStatus;
import cn.tolim.pudans.iam.authentication.totp.exception.TotpAlreadyEnrolledException;
import cn.tolim.pudans.iam.authentication.totp.exception.TotpLockedException;
import cn.tolim.pudans.iam.authentication.totp.exception.TotpNotEnrolledException;
import cn.tolim.pudans.iam.authentication.totp.repository.TotpCredentialRepository;
import cn.tolim.spissatus4j.contract.api.Spissatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TotpServiceTests {

    private static final String USER_ID = "user-1";

    private TotpCredentialRepository repository;
    private SpissatusTotpSecretProtector protector;
    private AdjustableClock clock;
    private TotpService service;

    @BeforeEach
    void setUp() {
        repository = mock(TotpCredentialRepository.class);
        TotpAuthenticationProperties properties = new TotpAuthenticationProperties();
        properties.getCrypto().setKeyProvider("memory");
        protector = new SpissatusTotpSecretProtector(emptyProvider(), properties);
        clock = new AdjustableClock(Instant.parse("2026-01-01T00:00:00Z"));
        service = new TotpService(repository, protector, new TotpCodeVerifier(), properties, clock);
        when(repository.save(any(TotpCredentialEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        protector.close();
    }

    @Test
    void enrollsPendingCredential() {
        when(repository.countByUserIdAndStatus(USER_ID, TotpStatus.ACTIVE)).thenReturn(0L);
        TotpEnrollResponse response = service.enroll(USER_ID, "phone");

        assertFalse(response.secret().isBlank());
        assertEquals("SHA1", response.algorithm());
        assertEquals(6, response.digits());
        assertEquals(30, response.periodSeconds());
        assertTrue(response.otpauthUri().startsWith("otpauth://totp/"));

        TotpCredentialEntity entity = lastSavedCredential();
        assertEquals(USER_ID, entity.getUserId());
        assertEquals(TotpStatus.PENDING, entity.getStatus());
        assertEquals(0, entity.getFailedAttempts());
        verify(repository).deleteAllByUserIdAndStatus(USER_ID, TotpStatus.PENDING);
    }

    @Test
    void rejectsEnrollmentWhenActiveCredentialExists() {
        when(repository.countByUserIdAndStatus(USER_ID, TotpStatus.ACTIVE)).thenReturn(1L);
        assertThrows(TotpAlreadyEnrolledException.class, () -> service.enroll(USER_ID, "phone"));
    }

    @Test
    void confirmsEnrollmentAndVerifiesCodes() {
        Enrollment enrollment = enroll();
        TotpCredentialEntity credential = enrollment.credential();
        when(repository.findByIdAndUserId("credential-1", USER_ID))
                .thenReturn(Optional.of(credential));
        when(repository.findFirstByUserIdAndStatusOrderByCreateTimeDesc(USER_ID, TotpStatus.ACTIVE))
                .thenReturn(Optional.of(credential));

        long firstStep = TotpCodeGenerator.timeStep(clock.instant(), 30);
        service.confirmEnrollment(USER_ID, "credential-1",
                code(enrollment, clock.instant()));
        assertEquals(TotpStatus.ACTIVE, credential.getStatus());
        assertEquals(firstStep, credential.getLastUsedStep());

        assertFalse(service.verify(USER_ID, code(enrollment, clock.instant())),
                "the code used to confirm must not be accepted again");

        clock.advance(Duration.ofSeconds(30));
        assertTrue(service.verify(USER_ID, code(enrollment, clock.instant())));
        assertEquals(firstStep + 1, credential.getLastUsedStep());
    }

    @Test
    void countsFailuresAndLocksCredential() {
        Enrollment enrollment = enroll();
        TotpCredentialEntity credential = enrollment.credential();
        credential.setStatus(TotpStatus.ACTIVE).setLastUsedStep(0L);
        when(repository.findFirstByUserIdAndStatusOrderByCreateTimeDesc(USER_ID, TotpStatus.ACTIVE))
                .thenReturn(Optional.of(credential));

        for (int attempt = 1; attempt <= 5; attempt++) {
            assertFalse(service.verify(USER_ID, "000000"));
            assertEquals(attempt, credential.getFailedAttempts());
        }
        assertThrows(TotpLockedException.class, () -> service.verify(USER_ID, "000000"));
    }

    @Test
    void requiresActiveEnrollment() {
        when(repository.findFirstByUserIdAndStatusOrderByCreateTimeDesc(USER_ID, TotpStatus.ACTIVE))
                .thenReturn(Optional.empty());
        assertThrows(TotpNotEnrolledException.class, () -> service.verify(USER_ID, "123456"));
    }

    @Test
    void listsAndDisablesCredentials() {
        Enrollment enrollment = enroll();
        TotpCredentialEntity credential = enrollment.credential();
        credential.setStatus(TotpStatus.ACTIVE);
        when(repository.findAllByUserIdOrderByCreateTimeDesc(USER_ID))
                .thenReturn(List.of(credential));
        assertEquals(1, service.list(USER_ID).size());
        assertEquals(1, service.disableAll(USER_ID));
        assertEquals(TotpStatus.DISABLED, credential.getStatus());
    }

    private Enrollment enroll() {
        when(repository.countByUserIdAndStatus(USER_ID, TotpStatus.ACTIVE)).thenReturn(0L);
        TotpEnrollResponse response = service.enroll(USER_ID, "phone");
        TotpCredentialEntity credential = lastSavedCredential();
        credential.setId("credential-1");
        byte[] secret = Base32Codec.decode(response.secret());
        return new Enrollment(credential, secret);
    }

    private TotpCredentialEntity lastSavedCredential() {
        ArgumentCaptor<TotpCredentialEntity> captor =
                ArgumentCaptor.forClass(TotpCredentialEntity.class);
        verify(repository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues().get(captor.getAllValues().size() - 1);
    }

    private static String code(Enrollment enrollment, Instant instant) {
        TotpCredentialEntity credential = enrollment.credential();
        return TotpCodeGenerator.generate(enrollment.secret(), TotpAlgorithm.SHA1,
                TotpCodeGenerator.timeStep(instant, credential.getPeriodSeconds()),
                credential.getDigits());
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<Spissatus> emptyProvider() {
        ObjectProvider<Spissatus> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }

    private record Enrollment(TotpCredentialEntity credential, byte[] secret) {
    }

    private static final class AdjustableClock extends Clock {
        private volatile Instant instant;

        private AdjustableClock(Instant instant) {
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