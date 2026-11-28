package cn.tolim.pudans.iam.endpoint;

import cn.tolim.pudans.contract.iam.dto.auth.LoginResponse;
import cn.tolim.pudans.contract.iam.dto.auth.OtpLoginRequest;
import cn.tolim.pudans.contract.iam.dto.auth.OtpSendRequest;
import cn.tolim.pudans.contract.iam.dto.auth.OtpSendResponse;
import cn.tolim.pudans.iam.contract.IamAuthApiPaths;
import cn.tolim.pudans.iam.core.auth.AuthenticationService;
import cn.tolim.pudans.iam.core.auth.otp.OtpLoginService;
import cn.tolim.pudans.iam.core.auth.user.UserAccountService.AuthenticatedAccount;
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

@Tag(name = "IAM 登录认证")
@RestController
public class IamOtpLoginEndpoint {

    private final OtpLoginService otpLoginService;
    private final AuthenticationService authenticationService;
    private final IamSessionCookieService cookieService;

    public IamOtpLoginEndpoint(OtpLoginService otpLoginService,
                               AuthenticationService authenticationService,
                               IamSessionCookieService cookieService) {
        this.otpLoginService = otpLoginService;
        this.authenticationService = authenticationService;
        this.cookieService = cookieService;
    }

    // Sends a login verification code to an email address or a phone number
    // Parameter request: OtpSendRequest
    // Returns: OtpSendResponse
    @Operation(summary = "发送登录验证码", operationId = "sendLoginOtp")
    @PostMapping(path = IamAuthApiPaths.OTP_SEND)
    public OtpSendResponse send(@RequestBody OtpSendRequest request) {
        return otpLoginService.sendCode(request);
    }

    // Verifies a login code, creates the global session and sets the cookie
    // Parameter request: OtpLoginRequest
    // Parameter httpRequest: HttpServletRequest
    // Parameter httpResponse: HttpServletResponse
    // Returns: ResponseEntity
    @Operation(summary = "验证码登录", operationId = "loginWithOtp")
    @PostMapping(path = IamAuthApiPaths.OTP_LOGIN)
    public ResponseEntity<LoginResponse> login(@RequestBody OtpLoginRequest request,
                                               HttpServletRequest httpRequest,
                                               HttpServletResponse httpResponse) {
        AuthenticatedAccount account = otpLoginService.authenticate(request);
        LoginResponse response = authenticationService.completeLogin(account,
                request.getAppCode(), request.getTargetUrl(),
                IamRequestUtils.clientIp(httpRequest), IamRequestUtils.userAgent(httpRequest));
        if (response.getSid() != null && !response.getSid().isBlank()) {
            cookieService.writeSessionCookie(httpResponse, response.getSid(),
                    response.getExpiresAt());
        }
        return ResponseEntity.ok(response);
    }
}