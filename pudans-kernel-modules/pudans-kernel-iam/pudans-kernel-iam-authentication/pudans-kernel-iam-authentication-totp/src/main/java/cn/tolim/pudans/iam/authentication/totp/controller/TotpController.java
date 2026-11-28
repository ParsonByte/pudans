package cn.tolim.pudans.iam.authentication.totp.controller;

import cn.tolim.pudans.iam.authentication.totp.constant.TotpAuthenticationConstants;
import cn.tolim.pudans.iam.authentication.totp.dto.TotpConfirmEnrollmentRequest;
import cn.tolim.pudans.iam.authentication.totp.dto.TotpCredentialResponse;
import cn.tolim.pudans.iam.authentication.totp.dto.TotpDisableResponse;
import cn.tolim.pudans.iam.authentication.totp.dto.TotpEnrollRequest;
import cn.tolim.pudans.iam.authentication.totp.dto.TotpEnrollResponse;
import cn.tolim.pudans.iam.authentication.totp.dto.TotpVerifyRequest;
import cn.tolim.pudans.iam.authentication.totp.dto.TotpVerifyResponse;
import cn.tolim.pudans.iam.authentication.totp.service.TotpService;
import cn.tolim.pudans.iam.infra.exception.PudansException;
import cn.tolim.pudans.iam.infra.security.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// TOTP MFA self-service endpoints for the authenticated user
@Tag(name = "TOTP 认证")
@RestController
@RequestMapping(TotpAuthenticationConstants.API_PATH)
public class TotpController {

    private final TotpService totpService;

    public TotpController(TotpService totpService) {
        this.totpService = totpService;
    }

    // Starts a new TOTP enrollment
    @Operation(summary = "开始 TOTP 绑定")
    @PostMapping("/enroll")
    public TotpEnrollResponse enroll(@Valid @RequestBody TotpEnrollRequest request) {
        return totpService.enroll(currentUserId(), request.name());
    }

    // Confirms a pending enrollment with the first generated code
    @Operation(summary = "确认 TOTP 绑定")
    @PostMapping("/enroll/confirm")
    public TotpCredentialResponse confirmEnrollment(
            @Valid @RequestBody TotpConfirmEnrollmentRequest request) {
        return totpService.confirmEnrollment(currentUserId(), request.credentialId(),
                request.code());
    }

    // Verifies a TOTP code for the current user
    @Operation(summary = "校验 TOTP")
    @PostMapping("/verify")
    public TotpVerifyResponse verify(@Valid @RequestBody TotpVerifyRequest request) {
        boolean valid = totpService.verify(currentUserId(), request.credentialId(), request.code());
        return new TotpVerifyResponse(valid);
    }

    // Lists the current user's credentials without secrets
    @Operation(summary = "获取 TOTP 凭证")
    @GetMapping("/credentials")
    public List<TotpCredentialResponse> credentials() {
        return totpService.list(currentUserId());
    }

    // Disables one credential of the current user
    @Operation(summary = "删除 TOTP 凭证")
    @DeleteMapping("/credentials/{credentialId}")
    public TotpCredentialResponse disable(@PathVariable String credentialId) {
        return totpService.disable(currentUserId(), credentialId);
    }

    // Disables every credential of the current user
    @Operation(summary = "删除全部 TOTP 凭证")
    @DeleteMapping("/credentials")
    public TotpDisableResponse disableAll() {
        return new TotpDisableResponse(totpService.disableAll(currentUserId()));
    }

    private String currentUserId() {
        if (!SecurityUtils.isAuthenticated()) {
            throw new PudansException("unauthenticated", "Authentication is required",
                    HttpStatus.UNAUTHORIZED);
        }
        String userId = SecurityUtils.getCurrentUserId();
        if (userId == null || userId.isBlank() || SecurityUtils.ANONYMOUS_USER.equals(userId)) {
            throw new PudansException("unauthenticated", "Authentication is required",
                    HttpStatus.UNAUTHORIZED);
        }
        return userId;
    }
}