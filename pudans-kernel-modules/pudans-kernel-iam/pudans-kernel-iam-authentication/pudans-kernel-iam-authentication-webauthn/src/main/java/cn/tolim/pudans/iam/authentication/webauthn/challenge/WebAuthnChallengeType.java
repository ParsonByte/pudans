package cn.tolim.pudans.iam.authentication.webauthn.challenge;

// Ceremony a WebAuthn challenge belongs to
public enum WebAuthnChallengeType {
    // navigator.credentials.create
    REGISTRATION,

    // navigator.credentials.get
    AUTHENTICATION
}