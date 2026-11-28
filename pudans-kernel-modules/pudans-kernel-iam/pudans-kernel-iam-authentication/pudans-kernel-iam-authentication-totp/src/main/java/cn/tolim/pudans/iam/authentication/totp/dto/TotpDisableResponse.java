package cn.tolim.pudans.iam.authentication.totp.dto;

// Number of TOTP credentials changed by a disable request
public record TotpDisableResponse(int disabledCount) {
}