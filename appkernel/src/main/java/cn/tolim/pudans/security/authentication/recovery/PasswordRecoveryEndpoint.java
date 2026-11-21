package cn.tolim.pudans.security.authentication.recovery;

import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.contract.infra.ValidationUtils;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.security.captcha.Captchas;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping(ApiGroups.API_PREFIX + "/recovery")
@RequiredArgsConstructor
@Tag(name = "PasswordRecoveryV1alpha1", description = "Password recovery endpoints.")
public class PasswordRecoveryEndpoint {

    private final PasswordRecovery passwordRecovery;

    private final Captchas captchas;

    @PostMapping("/send-code")
    @Operation(operationId = "SendPasswordRecoveryCode", summary = "Send a password recovery code.")
    @ApiResponse(responseCode = "204", description = "No Content")
    public Mono<ResponseEntity<Void>> sendCode(@Valid @RequestBody SendCodeRequest request) {
        return requireRecoveryCaptcha(request.captchaChallengeId(), request.captchaAnswer())
                .then(Mono.defer(() -> passwordRecovery.sendCode(request.email())))
                .thenReturn(ResponseEntity.noContent().build());
    }

    @PostMapping("/reset-password")
    @Operation(operationId = "ResetPasswordWithRecoveryCode", summary = "Reset a password with a recovery code.")
    @ApiResponse(responseCode = "204", description = "No Content")
    public Mono<ResponseEntity<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return requireRecoveryCaptcha(request.captchaChallengeId(), request.captchaAnswer())
                .then(Mono.defer(() -> passwordRecovery.resetPassword(
                        request.email(), request.code(), request.newPassword())))
                .thenReturn(ResponseEntity.noContent().build());
    }

    private Mono<Void> requireRecoveryCaptcha(String challengeId, String answer) {
        return captchas.recoveryEnabled().flatMap(enabled -> {
            if (!Boolean.TRUE.equals(enabled)) {
                return Mono.empty();
            }
            return captchas
                    .verify(challengeId, answer)
                    .flatMap(verified -> Boolean.TRUE.equals(verified)
                            ? Mono.empty()
                            : Mono.error(new ApiException(
                                    HttpStatus.BAD_REQUEST,
                                    "recovery-captcha-invalid",
                                    "验证码校验失败。")));
        });
    }

    public record SendCodeRequest(
            @NotBlank @Email @Size(max = 254) String email,
            String captchaChallengeId,
            String captchaAnswer) {}

    public record ResetPasswordRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Pattern(regexp = "\\d{6}") String code,
            @NotBlank
                    @Size(min = 8, max = 257)
                    @Pattern(regexp = ValidationUtils.PASSWORD_REGEX)
                    String newPassword,
            String captchaChallengeId,
            String captchaAnswer) {}
}
