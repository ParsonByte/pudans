package cn.tolim.pudans.iam.core.auth.mfa;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

// Short-lived MFA challenge issued after a successful password authentication
public record MfaChallenge(String challengeId, String userId, String userType, String orgId,
                           String username, String appCode, String targetUrl,
                           Set<MfaMethod> methods, Instant expiresAt) {

    public MfaChallenge {
        if (challengeId == null || challengeId.isBlank()) {
            throw new IllegalArgumentException("MFA challenge id must not be blank");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("MFA challenge user id must not be blank");
        }
        if (methods == null || methods.isEmpty()) {
            throw new IllegalArgumentException("MFA challenge methods must not be empty");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("MFA challenge expiry must not be null");
        }
        methods = Collections.unmodifiableSet(new LinkedHashSet<>(methods));
    }

    // Checks whether the challenge is no longer valid
    // Parameter now: Instant
    // Returns: boolean
    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    // Checks whether the challenge allows the given method
    // Parameter method: MfaMethod
    // Returns: boolean
    public boolean supports(MfaMethod method) {
        return method != null && methods.contains(method);
    }

    // Returns the preferred method shown to the browser
    // Returns: MfaMethod
    public MfaMethod preferredMethod() {
        return methods.iterator().next();
    }
}