package cn.tolim.pudans.iam.endpoint;

import cn.tolim.pudans.contract.iam.dto.auth.LoginResponse;
import cn.tolim.pudans.contract.iam.dto.auth.PasskeyLoginRequest;
import cn.tolim.pudans.contract.iam.dto.auth.PasskeyOptionsRequest;
import cn.tolim.pudans.iam.authentication.webauthn.dto.WebAuthnAuthenticationOptions;
import cn.tolim.pudans.iam.authentication.webauthn.service.WebAuthnService;
import cn.tolim.pudans.iam.authentication.webauthn.service.WebAuthnService.PasskeyAuthentication;
import cn.tolim.pudans.iam.authentication.webauthn.verification.WebAuthnAssertionRequest;
import cn.tolim.pudans.iam.contract.IamAuthApiPaths;
import cn.tolim.pudans.iam.core.auth.AuthenticationService;
import cn.tolim.pudans.iam.core.auth.exception.AuthenticationFailedException;
import cn.tolim.pudans.iam.core.auth.user.UserAccountService;
import cn.tolim.pudans.iam.core.auth.user.UserAccountService.AuthenticatedAccount;
import cn.tolim.pudans.iam.security.IamRequestUtils;
import cn.tolim.pudans.iam.security.IamSessionCookieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "IAM 登录认证")
@RestController
public class IamPasskeyLoginEndpoint {

    private final WebAuthnService webAuthnService;
    private final UserAccountService userAccountService;
    private final AuthenticationService authenticationService;
    private final IamSessionCookieService cookieService;

    public IamPasskeyLoginEndpoint(WebAuthnService webAuthnService,
                                   UserAccountService userAccountService,
                                   AuthenticationService authenticationService,
                                   IamSessionCookieService cookieService) {
        this.webAuthnService = webAuthnService;
        this.userAccountService = userAccountService;
        this.authenticationService = authenticationService;
        this.cookieService = cookieService;
    }

    // Creates passkey authentication options for a known or discoverable account
    // Parameter request: PasskeyOptionsRequest
    // Returns: WebAuthnAuthenticationOptions
    @Operation(summary = "获取通行密钥登录选项", operationId = "passkeyLoginOptions")
    @PostMapping(path = IamAuthApiPaths.PASSKEY_OPTIONS)
    public WebAuthnAuthenticationOptions options(
            @RequestBody(required = false) PasskeyOptionsRequest request) {
        String identifier = request == null ? null : StringUtils.trimToNull(request.getUsername());
        AuthenticatedAccount account = null;
        if (identifier != null) {
            account = userAccountService.findAccountByIdentifier(identifier)
                    .orElseThrow(AuthenticationFailedException::new);
        }
        return webAuthnService.authenticationOptions(
                account == null ? null : account.userId(),
                account == null ? null : account.username());
    }

    // Verifies a passkey assertion, creates the global session and sets the cookie
    // Parameter request: PasskeyLoginRequest
    // Parameter httpRequest: HttpServletRequest
    // Parameter httpResponse: HttpServletResponse
    // Returns: ResponseEntity
    @Operation(summary = "通行密钥登录", operationId = "loginWithPasskey")
    @PostMapping(path = IamAuthApiPaths.PASSKEY_VERIFY)
    public ResponseEntity<LoginResponse> verify(@RequestBody PasskeyLoginRequest request,
                                                HttpServletRequest httpRequest,
                                                HttpServletResponse httpResponse) {
        PasskeyAuthentication authentication = webAuthnService.authenticate(
                new WebAuthnAssertionRequest(request.getChallengeId(), request.getCredentialId(),
                        request.getClientDataJson(), request.getAuthenticatorData(),
                        request.getSignature()));
        AuthenticatedAccount account = userAccountService.requireAccountById(
                authentication.userId());
        LoginResponse response = authenticationService.completeLogin(account, null, null,
                IamRequestUtils.clientIp(httpRequest), IamRequestUtils.userAgent(httpRequest));
        if (response.getSid() != null && !response.getSid().isBlank()) {
            cookieService.writeSessionCookie(httpResponse, response.getSid(),
                    response.getExpiresAt());
        }
        return ResponseEntity.ok(response);
    }
}