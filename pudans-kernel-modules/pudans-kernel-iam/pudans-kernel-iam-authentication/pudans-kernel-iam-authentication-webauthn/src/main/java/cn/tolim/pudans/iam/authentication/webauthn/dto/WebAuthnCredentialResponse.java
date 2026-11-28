package cn.tolim.pudans.iam.authentication.webauthn.dto;

import java.time.LocalDateTime;

// Safe WebAuthn credential metadata
public record WebAuthnCredentialResponse(String credentialId, String transports, String aaguid,
                                         String attestationFormat, int coseAlgorithm,
                                         long signCount, boolean backupEligible,
                                         boolean backupState, String status,
                                         LocalDateTime registeredAt,
                                         LocalDateTime lastUsedAt) {
}