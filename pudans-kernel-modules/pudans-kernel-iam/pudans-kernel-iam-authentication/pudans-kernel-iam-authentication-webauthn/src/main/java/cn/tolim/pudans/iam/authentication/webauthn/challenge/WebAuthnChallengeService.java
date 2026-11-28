package cn.tolim.pudans.iam.authentication.webauthn.challenge;

// Creates and consumes one-time WebAuthn challenges
public interface WebAuthnChallengeService {

    // Creates a registration challenge bound to the enrolling user
    WebAuthnChallenge createRegistration(String userId, String username);

    // Creates an authentication challenge, the user may be unknown for usernameless login
    WebAuthnChallenge createAuthentication(String userId, String username);

    // Consumes a challenge exactly once and validates its ceremony type
    WebAuthnChallenge consume(String challengeId, WebAuthnChallengeType type);
}