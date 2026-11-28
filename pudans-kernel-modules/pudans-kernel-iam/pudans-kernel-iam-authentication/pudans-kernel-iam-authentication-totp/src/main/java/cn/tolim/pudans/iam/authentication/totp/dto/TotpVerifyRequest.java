package cn.tolim.pudans.iam.authentication.totp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// TOTP verification request
public record TotpVerifyRequest(String credentialId,
                                @NotBlank(message = "TOTP code must not be blank")
                                @Size(max = 10, message = "TOTP code is too long")
                                String code) {
}