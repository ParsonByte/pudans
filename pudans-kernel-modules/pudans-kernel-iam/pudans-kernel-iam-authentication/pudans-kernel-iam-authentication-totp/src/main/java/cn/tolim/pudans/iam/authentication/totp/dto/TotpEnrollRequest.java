package cn.tolim.pudans.iam.authentication.totp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// TOTP enrollment request
public record TotpEnrollRequest(@NotBlank(message = "TOTP account name must not be blank")
                                @Size(max = 128, message = "TOTP account name is too long")
                                String name) {
}