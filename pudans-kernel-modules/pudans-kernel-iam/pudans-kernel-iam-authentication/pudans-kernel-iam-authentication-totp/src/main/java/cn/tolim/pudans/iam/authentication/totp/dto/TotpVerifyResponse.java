package cn.tolim.pudans.iam.authentication.totp.dto;

// TOTP verification result
public record TotpVerifyResponse(boolean valid) {
}