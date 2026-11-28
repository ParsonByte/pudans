package cn.tolim.pudans.iam.authentication.webauthn.controller;

import cn.tolim.pudans.iam.authentication.webauthn.constant.WebAuthnConstants;
import cn.tolim.pudans.iam.authentication.webauthn.dto.WebAuthnAuthenticationOptions;
import cn.tolim.pudans.iam.authentication.webauthn.dto.WebAuthnCredentialResponse;
import cn.tolim.pudans.iam.authentication.webauthn.dto.WebAuthnDisableResponse;
import cn.tolim.pudans.iam.authentication.webauthn.dto.WebAuthnRegistrationOptions;
import cn.tolim.pudans.iam.authentication.webauthn.service.WebAuthnService;
import cn.tolim.pudans.iam.authentication.webauthn.verification.WebAuthnAssertionRequest;
import cn.tolim.pudans.iam.authentication.webauthn.verification.WebAuthnRegistrationRequest;
import cn.tolim.pudans.iam.infra.exception.PudansException;
import cn.tolim.pudans.iam.infra.security.userdetails.UserDetails;
import cn.tolim.pudans.iam.infra.security.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// WebAuthn MFA self-service endpoints for the authenticated user
@Tag(name = "WebAuthn 认证")
@RestController
@RequestMapping(WebAuthnConstants.API_PATH)
public class WebAuthnController {

    private final WebAuthnService webAuthnService;

    public WebAuthnController(WebAuthnService webAuthnService) {
        this.webAuthnService = webAuthnService;
    }

    // Creates registration options for the current user
    @Operation(summary = "开始 WebAuthn 注册")
    @PostMapping("/registration/options")
    public WebAuthnRegistrationOptions registrationOptions() {
        UserDetails user = currentUser();
        return webAuthnService.registrationOptions(user.getId(), user.getUsername(),
                displayName(user));
    }

    // Completes registration for the current user
    @Operation(summary = "完成 WebAuthn 注册")
    @PostMapping("/registration/complete")
    public WebAuthnCredentialResponse completeRegistration(
            @RequestBody WebAuthnRegistrationRequest request) {
        return webAuthnService.completeRegistration(currentUser().getId(), request);
    }

    // Creates authentication options for the current user
    @Operation(summary = "获取 WebAuthn 认证选项")
    @PostMapping("/authentication/options")
    public WebAuthnAuthenticationOptions authenticationOptions() {
        UserDetails user = currentUser();
        return webAuthnService.authenticationOptions(user.getId(), user.getUsername());
    }

    // Completes authentication for the current user
    @Operation(summary = "完成 WebAuthn 认证")
    @PostMapping("/authentication/complete")
    public WebAuthnCredentialResponse completeAuthentication(
            @RequestBody WebAuthnAssertionRequest request) {
        return webAuthnService.completeAuthentication(currentUser().getId(), request);
    }

    // Lists the current user's credentials
    @Operation(summary = "获取 WebAuthn 凭证")
    @GetMapping("/credentials")
    public List<WebAuthnCredentialResponse> credentials() {
        return webAuthnService.list(currentUser().getId());
    }

    // Disables one credential of the current user
    @Operation(summary = "删除 WebAuthn 凭证")
    @DeleteMapping("/credentials/{credentialId}")
    public WebAuthnCredentialResponse disable(@PathVariable String credentialId) {
        return webAuthnService.disable(currentUser().getId(), credentialId);
    }

    // Disables every credential of the current user
    @Operation(summary = "删除全部 WebAuthn 凭证")
    @DeleteMapping("/credentials")
    public WebAuthnDisableResponse disableAll() {
        return new WebAuthnDisableResponse(webAuthnService.disableAll(currentUser().getId()));
    }

    private UserDetails currentUser() {
        if (!SecurityUtils.isAuthenticated()) {
            throw new PudansException("unauthenticated", "Authentication is required",
                    HttpStatus.UNAUTHORIZED);
        }
        UserDetails user = SecurityUtils.getCurrentUser();
        if (user == null || user.getId() == null || user.getId().isBlank()) {
            throw new PudansException("unauthenticated", "Authentication is required",
                    HttpStatus.UNAUTHORIZED);
        }
        return user;
    }

    private static String displayName(UserDetails user) {
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName();
        }
        if (user.getNickName() != null && !user.getNickName().isBlank()) {
            return user.getNickName();
        }
        return user.getUsername();
    }
}