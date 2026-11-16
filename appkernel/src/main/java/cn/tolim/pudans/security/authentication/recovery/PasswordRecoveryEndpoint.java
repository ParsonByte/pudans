package cn.tolim.pudans.security.authentication.recovery;

import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.contract.infra.ValidationUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping(ApiGroups.API_PREFIX + "/recovery")
@RequiredArgsConstructor
public class PasswordRecoveryEndpoint {

    private final PasswordRecoveryService passwordRecoveryService;

    @PostMapping("/send-code")
    public Mono<ResponseEntity<Void>> sendCode(@Valid @RequestBody SendCodeRequest request) {
        return passwordRecoveryService
                .sendCode(request.email())
                .thenReturn(ResponseEntity.noContent().build());
    }

    @PostMapping("/reset-password")
    public Mono<ResponseEntity<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return passwordRecoveryService
                .resetPassword(request.email(), request.code(), request.newPassword())
                .thenReturn(ResponseEntity.noContent().build());
    }

    public record SendCodeRequest(
            @NotBlank @Email @Size(max = 254) String email) {}

    public record ResetPasswordRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Pattern(regexp = "\\d{6}") String code,
            @NotBlank
                    @Size(min = 8, max = 257)
                    @Pattern(regexp = ValidationUtils.PASSWORD_REGEX)
                    String newPassword) {}
}
