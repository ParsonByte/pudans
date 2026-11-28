package cn.tolim.pudans.iam.authentication.totp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Confirms a pending TOTP enrollment with a generated code
public record TotpConfirmEnrollmentRequest(String credentialId,
                                           @NotBlank(message = "TOTP code must not be blank")
                                           @Size(max = 10, message = "TOTP code is too long")
                                           String code) {
}