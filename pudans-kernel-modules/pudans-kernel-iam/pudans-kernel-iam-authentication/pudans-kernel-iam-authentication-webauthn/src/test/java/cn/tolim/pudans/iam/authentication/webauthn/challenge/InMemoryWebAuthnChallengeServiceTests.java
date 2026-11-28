package cn.tolim.pudans.iam.authentication.webauthn.challenge;

import cn.tolim.pudans.iam.authentication.webauthn.config.WebAuthnProperties;
import cn.tolim.pudans.iam.authentication.webauthn.exception.WebAuthnChallengeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryWebAuthnChallengeServiceTests {

    private AdjustableClock clock;
    private InMemoryWebAuthnChallengeService service;

    @BeforeEach
    void setUp() {
        WebAuthnProperties properties = new WebAuthnProperties();
        properties.setChallengeTtl(Duration.ofMinutes(1));
        clock = new AdjustableClock(Instant.parse("2026-01-01T00:00:00Z"));
        service = new InMemoryWebAuthnChallengeService(properties, clock);
    }

    @Test
    void createsUniqueChallenges() {
        WebAuthnChallenge first = service.createRegistration("user-1", "alice");
        WebAuthnChallenge second = service.createRegistration("user-2", "bob");
        assertFalse(first.challenge().isBlank());
        assertFalse(first.challenge().equals(second.challenge()));
        assertEquals(WebAuthnChallengeType.REGISTRATION, first.type());
        assertEquals("user-1", first.userId());
    }

    @Test
    void consumesChallengeExactlyOnce() {
        WebAuthnChallenge challenge = service.createAuthentication(null, "alice");
        WebAuthnChallenge consumed = service.consume(challenge.challengeId(),
                WebAuthnChallengeType.AUTHENTICATION);
        assertEquals(challenge.challenge(), consumed.challenge());
        assertThrows(WebAuthnChallengeException.class,
                () -> service.consume(challenge.challengeId(),
                        WebAuthnChallengeType.AUTHENTICATION));
    }

    @Test
    void rejectsChallengeTypeMismatch() {
        WebAuthnChallenge challenge = service.createRegistration("user-1", "alice");
        assertThrows(WebAuthnChallengeException.class,
                () -> service.consume(challenge.challengeId(),
                        WebAuthnChallengeType.AUTHENTICATION));
    }

    @Test
    void rejectsExpiredChallenges() {
        WebAuthnChallenge challenge = service.createRegistration("user-1", "alice");
        clock.advance(Duration.ofMinutes(2));
        assertThrows(WebAuthnChallengeException.class,
                () -> service.consume(challenge.challengeId(),
                        WebAuthnChallengeType.REGISTRATION));
    }

    @Test
    void rejectsUnknownChallengeId() {
        assertThrows(WebAuthnChallengeException.class,
                () -> service.consume("missing", WebAuthnChallengeType.REGISTRATION));
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