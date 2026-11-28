package cn.tolim.pudans.iam.authentication.webauthn.challenge;

import cn.tolim.pudans.iam.authentication.webauthn.config.WebAuthnProperties;
import cn.tolim.pudans.iam.authentication.webauthn.crypto.WebAuthnEncoding;
import cn.tolim.pudans.iam.authentication.webauthn.exception.WebAuthnChallengeException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

// Process-local challenge store with expiry and single use semantics
public class InMemoryWebAuthnChallengeService implements WebAuthnChallengeService {

    private static final int MAX_ENTRIES = 10_000;
    private static final int CHALLENGE_ID_BYTES = 16;

    private final WebAuthnProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, WebAuthnChallenge> challenges = new ConcurrentHashMap<>();

    @Autowired
    public InMemoryWebAuthnChallengeService(WebAuthnProperties properties,
                                            ObjectProvider<Clock> clockProvider) {
        this(properties, clockProvider.getIfAvailable(Clock::systemUTC));
    }

    public InMemoryWebAuthnChallengeService(WebAuthnProperties properties, Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public WebAuthnChallenge createRegistration(String userId, String username) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Registration challenge requires a user ID");
        }
        return create(WebAuthnChallengeType.REGISTRATION, userId, username);
    }

    @Override
    public WebAuthnChallenge createAuthentication(String userId, String username) {
        return create(WebAuthnChallengeType.AUTHENTICATION, userId, username);
    }

    @Override
    public WebAuthnChallenge consume(String challengeId, WebAuthnChallengeType type) {
        if (challengeId == null || challengeId.isBlank()) {
            throw new WebAuthnChallengeException("WebAuthn challenge ID is required");
        }
        WebAuthnChallenge challenge = challenges.remove(challengeId);
        if (challenge == null) {
            throw new WebAuthnChallengeException("WebAuthn challenge is unknown or already used");
        }
        if (challenge.isExpired(clock.instant())) {
            throw new WebAuthnChallengeException("WebAuthn challenge has expired");
        }
        if (challenge.type() != type) {
            throw new WebAuthnChallengeException("WebAuthn challenge type does not match");
        }
        return challenge;
    }

    private WebAuthnChallenge create(WebAuthnChallengeType type, String userId, String username) {
        removeExpired();
        if (challenges.size() >= MAX_ENTRIES) {
            throw new WebAuthnChallengeException("Too many pending WebAuthn challenges");
        }
        byte[] challengeBytes = new byte[Math.max(properties.getChallengeBytes(), 16)];
        random.nextBytes(challengeBytes);
        byte[] idBytes = new byte[CHALLENGE_ID_BYTES];
        random.nextBytes(idBytes);
        WebAuthnChallenge challenge = new WebAuthnChallenge(
                WebAuthnEncoding.base64Url(idBytes),
                WebAuthnEncoding.base64Url(challengeBytes),
                userId, username, type, clock.instant().plus(properties.getChallengeTtl()));
        challenges.put(challenge.challengeId(), challenge);
        return challenge;
    }

    private void removeExpired() {
        java.time.Instant now = clock.instant();
        challenges.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }
}