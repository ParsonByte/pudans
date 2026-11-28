package cn.tolim.pudans.iam.core.auth.mfa;

import cn.tolim.pudans.iam.core.auth.IamAuthProperties;
import cn.tolim.pudans.iam.core.auth.mfa.exception.MfaChallengeInvalidException;
import cn.tolim.pudans.iam.core.auth.session.SessionTokenGenerator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Stores short-lived MFA challenges issued between password authentication and second factor verification
@Service
public class MfaChallengeService {

    private final Map<String, MfaChallenge> challenges = new ConcurrentHashMap<>();

    private final IamAuthProperties properties;
    private final SessionTokenGenerator tokenGenerator;
    private final Clock clock;

    @Autowired
    public MfaChallengeService(IamAuthProperties properties, SessionTokenGenerator tokenGenerator,
                               ObjectProvider<Clock> clockProvider) {
        this(properties, tokenGenerator, clockProvider.getIfAvailable(Clock::systemUTC));
    }

    public MfaChallengeService(IamAuthProperties properties, SessionTokenGenerator tokenGenerator,
                               Clock clock) {
        this.properties = properties;
        this.tokenGenerator = tokenGenerator;
        this.clock = clock;
    }

    // Creates and stores a new challenge for the authenticated account
    // Parameter userId: String
    // Parameter userType: String
    // Parameter orgId: String
    // Parameter username: String
    // Parameter appCode: String
    // Parameter targetUrl: String
    // Parameter methods: Set
    // Returns: MfaChallenge
    public MfaChallenge create(String userId, String userType, String orgId, String username,
                               String appCode, String targetUrl, Set<MfaMethod> methods) {
        Instant now = clock.instant();
        purgeExpired(now);
        MfaChallenge challenge = new MfaChallenge(tokenGenerator.generate(), userId, userType,
                orgId, username, appCode, targetUrl, methods, now.plus(effectiveTtl()));
        challenges.put(challenge.challengeId(), challenge);
        return challenge;
    }

    // Resolves a challenge without consuming it
    // Parameter challengeId: String
    // Returns: MfaChallenge
    public MfaChallenge require(String challengeId) {
        if (challengeId == null || challengeId.isBlank()) {
            throw new MfaChallengeInvalidException();
        }
        MfaChallenge challenge = challenges.get(challengeId);
        if (challenge == null) {
            throw new MfaChallengeInvalidException();
        }
        if (challenge.isExpired(clock.instant())) {
            challenges.remove(challengeId, challenge);
            throw new MfaChallengeInvalidException("MFA challenge has expired");
        }
        return challenge;
    }

    // Consumes a challenge exactly once
    // Parameter challengeId: String
    // Returns: MfaChallenge
    public MfaChallenge consume(String challengeId) {
        MfaChallenge challenge = require(challengeId);
        if (!challenges.remove(challengeId, challenge)) {
            throw new MfaChallengeInvalidException("MFA challenge has already been used");
        }
        return challenge;
    }

    // Finds the newest pending challenge of a username, optionally restricted to an application
    // Parameter username: String
    // Parameter appCode: String
    // Returns: Optional<MfaChallenge>
    public Optional<MfaChallenge> findLatest(String username, String appCode) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        MfaChallenge latest = null;
        for (Map.Entry<String, MfaChallenge> entry : challenges.entrySet()) {
            MfaChallenge challenge = entry.getValue();
            if (challenge.isExpired(now)) {
                challenges.remove(entry.getKey(), challenge);
                continue;
            }
            if (!challenge.username().equals(username)) {
                continue;
            }
            if (appCode != null && !appCode.isBlank() && !appCode.equals(challenge.appCode())) {
                continue;
            }
            if (latest == null || challenge.expiresAt().isAfter(latest.expiresAt())) {
                latest = challenge;
            }
        }
        return Optional.ofNullable(latest);
    }

    private void purgeExpired(Instant now) {
        challenges.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private Duration effectiveTtl() {
        Duration ttl = properties.getMfa().getChallengeTtl();
        return ttl == null || ttl.isNegative() || ttl.isZero() ? Duration.ofMinutes(5) : ttl;
    }
}