package cn.tolim.pudans.iam.endpoint;

import cn.tolim.pudans.contract.iam.dto.auth.LoginResponse;
import cn.tolim.pudans.contract.iam.dto.auth.MfaChallengeRequest;
import cn.tolim.pudans.contract.iam.dto.auth.MfaChallengeResponse;
import cn.tolim.pudans.contract.iam.dto.auth.MfaVerifyRequest;
import cn.tolim.pudans.iam.contract.IamAuthApiPaths;
import cn.tolim.pudans.iam.contract.dto.IamMfaWebAuthnOptionsRequest;
import cn.tolim.pudans.iam.contract.dto.IamMfaWebAuthnVerifyRequest;
import cn.tolim.pudans.iam.authentication.webauthn.dto.WebAuthnAuthenticationOptions;
import cn.tolim.pudans.iam.authentication.webauthn.service.WebAuthnService;
import cn.tolim.pudans.iam.authentication.webauthn.verification.WebAuthnAssertionRequest;
import cn.tolim.pudans.iam.core.auth.AuthenticationService;
import cn.tolim.pudans.iam.core.auth.exception.InvalidAuthRequestException;
import cn.tolim.pudans.iam.core.auth.mfa.MfaChallenge;
import cn.tolim.pudans.iam.core.auth.mfa.MfaMethod;
import cn.tolim.pudans.iam.security.IamRequestUtils;
import cn.tolim.pudans.iam.security.IamSessionCookieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "IAM 多因素认证")
@RestController
public class IamMfaEndpoint {

    private final AuthenticationService authenticationService;
    private final WebAuthnService webAuthnService;
    private final IamSessionCookieService cookieService;

    public IamMfaEndpoint(AuthenticationService authenticationService,
                          WebAuthnService webAuthnService,
                          IamSessionCookieService cookieService) {
        this.authenticationService = authenticationService;
        this.webAuthnService = webAuthnService;
        this.cookieService = cookieService;
    }

    // Pending MFA challenge metadata for the login page
    // Parameter request: MfaChallengeRequest
    // Returns: MfaChallengeResponse
    @Operation(summary = "发起 MFA 挑战")
    @PostMapping(path = IamAuthApiPaths.MFA_CHALLENGE)
    public MfaChallengeResponse challenge(@RequestBody MfaChallengeRequest request) {
        return authenticationService.mfaChallenge(request);
    }

    // TOTP verification, completes the global login session and sets the session cookie
    // Parameter request: MfaVerifyRequest
    // Parameter httpRequest: HttpServletRequest
    // Parameter httpResponse: HttpServletResponse
    // Returns: ResponseEntity
    @Operation(summary = "校验 MFA")
    @PostMapping(path = IamAuthApiPaths.MFA_VERIFY)
    public ResponseEntity<LoginResponse> verify(@RequestBody MfaVerifyRequest request,
                                                HttpServletRequest httpRequest,
                                                HttpServletResponse httpResponse) {
        LoginResponse response = authenticationService.verifyMfa(request,
                IamRequestUtils.clientIp(httpRequest), IamRequestUtils.userAgent(httpRequest));
        cookieService.writeSessionCookie(httpResponse, response.getSid(), response.getExpiresAt());
        return ResponseEntity.ok(response);
    }

    // Creates WebAuthn authentication options bound to a pending MFA challenge
    // Parameter request: IamMfaWebAuthnOptionsRequest
    // Returns: WebAuthnAuthenticationOptions
    @Operation(summary = "获取 WebAuthn 认证选项")
    @PostMapping(path = IamAuthApiPaths.MFA_WEBAUTHN_OPTIONS)
    public WebAuthnAuthenticationOptions webAuthnOptions(
            @RequestBody IamMfaWebAuthnOptionsRequest request) {
        MfaChallenge challenge = authenticationService.requireMfaChallenge(
                request.getChallengeId(), request.getUsername(), request.getAppCode(),
                MfaMethod.WEBAUTHN);
        return webAuthnService.authenticationOptions(challenge.userId(), challenge.username());
    }

    // WebAuthn assertion verification, completes the global login session and sets the cookie
    // Parameter request: IamMfaWebAuthnVerifyRequest
    // Parameter httpRequest: HttpServletRequest
    // Parameter httpResponse: HttpServletResponse
    // Returns: ResponseEntity
    @Operation(summary = "校验 WebAuthn 认证")
    @PostMapping(path = IamAuthApiPaths.MFA_WEBAUTHN_VERIFY)
    public ResponseEntity<LoginResponse> webAuthnVerify(
            @RequestBody IamMfaWebAuthnVerifyRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        requireWebAuthnFields(request);
        String ip = IamRequestUtils.clientIp(httpRequest);
        String userAgent = IamRequestUtils.userAgent(httpRequest);
        MfaChallenge challenge = authenticationService.requireMfaChallenge(
                request.getChallengeId(), request.getUsername(), request.getAppCode(),
                MfaMethod.WEBAUTHN);
        try {
            webAuthnService.completeAuthentication(challenge.userId(),
                    new WebAuthnAssertionRequest(request.getWebAuthnChallengeId(),
                            request.getCredentialId(), request.getClientDataJson(),
                            request.getAuthenticatorData(), request.getSignature()));
        } catch (RuntimeException exception) {
            authenticationService.auditMfaFailure(challenge, ip, userAgent,
                    exception.getMessage());
            throw exception;
        }
        LoginResponse response = authenticationService.completeMfaLogin(
                challenge.challengeId(), ip, userAgent);
        cookieService.writeSessionCookie(httpResponse, response.getSid(), response.getExpiresAt());
        return ResponseEntity.ok(response);
    }

    private void requireWebAuthnFields(IamMfaWebAuthnVerifyRequest request) {
        if (request == null || isBlank(request.getChallengeId())
                || isBlank(request.getWebAuthnChallengeId()) || isBlank(request.getCredentialId())
                || isBlank(request.getClientDataJson()) || isBlank(request.getAuthenticatorData())
                || isBlank(request.getSignature())) {
            throw new InvalidAuthRequestException("mfa_webauthn_request_invalid",
                    "The WebAuthn assertion is incomplete");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}