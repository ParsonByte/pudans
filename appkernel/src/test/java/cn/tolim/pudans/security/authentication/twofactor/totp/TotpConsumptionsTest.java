package cn.tolim.pudans.security.authentication.twofactor.totp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.user.resource.User;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.security.authentication.exception.TwoFactorAuthException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class TotpConsumptionsTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    TotpSecrets totpSecrets;

    private TotpConsumptions service;

    @BeforeEach
    void setUp() {
        service = new TotpConsumptions(client, totpSecrets);
        service.setClock(Clock.fixed(Instant.parse("2026-11-17T00:00:00Z"), ZoneOffset.UTC));
        when(totpSecrets.decryptSecret("enc")).thenReturn("raw");
    }

    private static User user(Long lastStep) {
        var user = new User();
        var metadata = new Metadata();
        metadata.setName("alice");
        user.setMetadata(metadata);
        var spec = new User.UserSpec();
        spec.setTotpEncryptedSecret("enc");
        spec.setTotpLastStep(lastStep);
        user.setSpec(spec);
        return user;
    }

    @Test
    void consumesAndStoresTheAcceptedStep() {
        var user = user(null);
        when(client.fetch(User.class, "alice")).thenReturn(Mono.just(user));
        when(totpSecrets.matchStep(eq("raw"), eq(123456), anyLong())).thenReturn(10L);
        when(client.update(user)).thenReturn(Mono.just(user));

        StepVerifier.create(service.consume(user, 123456))
                .assertNext(updated -> assertThat(updated.getSpec().getTotpLastStep()).isEqualTo(10L))
                .verifyComplete();
    }

    @Test
    void rejectsAReplayOfTheSameStep() {
        var user = user(10L);
        when(client.fetch(User.class, "alice")).thenReturn(Mono.just(user));
        when(totpSecrets.matchStep(eq("raw"), eq(123456), anyLong())).thenReturn(10L);

        StepVerifier.create(service.consume(user, 123456))
                .expectError(TwoFactorAuthException.class)
                .verify();
    }

    @Test
    void rejectsAConcurrentReplayAfterAnOptimisticConflict() {
        var first = user(null);
        var second = user(10L);
        when(client.fetch(User.class, "alice")).thenReturn(Mono.just(first), Mono.just(second));
        when(totpSecrets.matchStep(eq("raw"), eq(123456), anyLong())).thenReturn(10L);
        when(client.update(first)).thenReturn(Mono.error(new OptimisticLockingFailureException("conflict")));

        StepVerifier.create(service.consume(first, 123456))
                .expectError(TwoFactorAuthException.class)
                .verify();
    }
}
