package cn.tolim.pudans.iam.core.auth.mfa;

import cn.tolim.pudans.iam.core.auth.IamAuthProperties;
import cn.tolim.pudans.iam.core.auth.mfa.exception.MfaChallengeInvalidException;
import cn.tolim.pudans.iam.core.auth.session.SessionTokenGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MfaChallengeServiceTests {

    private MutableClock clock;
    private MfaChallengeService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        service = new MfaChallengeService(new IamAuthProperties(), new SessionTokenGenerator(),
                clock);
    }

    @Test
    void createsChallengeWithConfiguredTtl() {
        MfaChallenge challenge = service.create("user-1", "USER", "org-1", "alice", "order",
                "/orders", Set.of(MfaMethod.TOTP));

        assertThat(challenge.challengeId()).isNotBlank();
        assertThat(challenge.expiresAt()).isEqualTo(Instant.parse("2026-01-01T00:05:00Z"));
        assertThat(challenge.preferredMethod()).isEqualTo(MfaMethod.TOTP);
        assertThat(service.require(challenge.challengeId())).isEqualTo(challenge);
    }

    @Test
    void consumesChallengeOnlyOnce() {
        MfaChallenge challenge = service.create("user-1", "USER", null, "alice", null, null,
                Set.of(MfaMethod.TOTP));

        assertThat(service.consume(challenge.challengeId())).isEqualTo(challenge);
        assertThatThrownBy(() -> service.consume(challenge.challengeId()))
                .isInstanceOf(MfaChallengeInvalidException.class);
    }

    @Test
    void rejectsExpiredChallenge() {
        MfaChallenge challenge = service.create("user-1", "USER", null, "alice", null, null,
                Set.of(MfaMethod.TOTP));

        clock.advance(Duration.ofMinutes(6));

        assertThatThrownBy(() -> service.require(challenge.challengeId()))
                .isInstanceOf(MfaChallengeInvalidException.class)
                .hasMessageContaining("expired");
        assertThat(service.findLatest("alice", null)).isEmpty();
    }

    @Test
    void rejectsBlankChallengeId() {
        assertThatThrownBy(() -> service.require(" "))
                .isInstanceOf(MfaChallengeInvalidException.class);
    }

    @Test
    void findsLatestChallengeOfUserAndApplication() {
        MfaChallenge older = service.create("user-1", "USER", null, "alice", "order", null,
                Set.of(MfaMethod.TOTP));
        clock.advance(Duration.ofSeconds(1));
        MfaChallenge newer = service.create("user-1", "USER", null, "alice", "order", null,
                Set.of(MfaMethod.TOTP));
        service.create("user-2", "USER", null, "bob", "order", null, Set.of(MfaMethod.TOTP));

        Optional<MfaChallenge> latest = service.findLatest("alice", "order");

        assertThat(latest).contains(newer);
        assertThat(latest).isNotEqualTo(Optional.of(older));
        assertThat(service.findLatest("alice", "another-app")).isEmpty();
        assertThat(service.findLatest("alice", null)).contains(newer);
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