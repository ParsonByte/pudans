package cn.tolim.pudans.iam.authentication.totp.dto;

// TOTP enrollment result, the secret is returned exactly once
public record TotpEnrollResponse(String credentialId, String secret, String otpauthUri,
                                 String algorithm, int digits, int periodSeconds) {
}