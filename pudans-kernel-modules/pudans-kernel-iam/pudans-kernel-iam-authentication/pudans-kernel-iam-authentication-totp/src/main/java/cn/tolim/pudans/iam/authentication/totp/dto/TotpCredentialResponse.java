package cn.tolim.pudans.iam.authentication.totp.dto;

import java.time.LocalDateTime;

// Safe TOTP credential metadata, never contains the secret
public record TotpCredentialResponse(String credentialId, String name, String status,
                                     String algorithm, int digits, int periodSeconds,
                                     LocalDateTime confirmedAt, LocalDateTime lastUsedAt) {
}