package cn.tolim.pudans.iam.authentication.webauthn.challenge;

import java.time.Instant;

// One-time WebAuthn ceremony challenge
public record WebAuthnChallenge(String challengeId, String challenge, String userId,
                                String username, WebAuthnChallengeType type,
                                Instant expiresAt) {

    public WebAuthnChallenge {
        if (challengeId == null || challengeId.isBlank()) {
            throw new IllegalArgumentException("Challenge ID must not be blank");
        }
        if (challenge == null || challenge.isBlank()) {
            throw new IllegalArgumentException("Challenge value must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("Challenge type must not be null");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("Challenge expiry must not be null");
        }
    }

    // Returns true when the challenge is no longer valid
    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}