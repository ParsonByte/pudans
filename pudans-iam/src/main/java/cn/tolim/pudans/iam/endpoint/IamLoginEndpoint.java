package cn.tolim.pudans.iam.endpoint;

import cn.tolim.pudans.contract.iam.dto.auth.LoginConfigResponse;
import cn.tolim.pudans.contract.iam.dto.auth.LoginRequest;
import cn.tolim.pudans.contract.iam.dto.auth.LoginResponse;
import cn.tolim.pudans.contract.iam.dto.auth.LogoutRequest;
import cn.tolim.pudans.contract.iam.dto.auth.LogoutResponse;
import cn.tolim.pudans.iam.contract.IamAuthApiPaths;
import cn.tolim.pudans.iam.core.auth.AuthenticationService;
import cn.tolim.pudans.iam.security.IamRequestUtils;
import cn.tolim.pudans.iam.security.IamSessionCookieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "IAM 登录认证")
@RestController
public class IamLoginEndpoint {

    private final AuthenticationService authenticationService;
    private final IamSessionCookieService cookieService;

    public IamLoginEndpoint(AuthenticationService authenticationService,
                            IamSessionCookieService cookieService) {
        this.authenticationService = authenticationService;
        this.cookieService = cookieService;
    }

    // Password login, creates the global login session and sets the HttpOnly session cookie
    // Parameter request: LoginRequest
    // Parameter httpRequest: HttpServletRequest
    // Parameter httpResponse: HttpServletResponse
    // Returns: ResponseEntity
    @Operation(summary = "登录")
    @PostMapping(path = IamAuthApiPaths.LOGIN)
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest,
                                               HttpServletResponse httpResponse) {
        String ip = request.getIp() != null && !request.getIp().isBlank()
                ? request.getIp() : IamRequestUtils.clientIp(httpRequest);
        String userAgent = request.getUserAgent() != null && !request.getUserAgent().isBlank()
                ? request.getUserAgent() : IamRequestUtils.userAgent(httpRequest);
        LoginResponse response = authenticationService.login(request, ip, userAgent);
        // Only a completed login creates the global session cookie, MFA challenges do not
        if (response.getSid() != null && !response.getSid().isBlank()) {
            cookieService.writeSessionCookie(httpResponse, response.getSid(), response.getExpiresAt());
        }
        return ResponseEntity.ok(response);
    }

    // Login page configuration
    // Parameter appCode: String
    // Returns: LoginConfigResponse
    @Operation(summary = "获取登录配置")
    @GetMapping(path = IamAuthApiPaths.LOGIN_CONFIG)
    public LoginConfigResponse loginConfig(@RequestParam(value = "appCode", required = false) String appCode) {
        return authenticationService.loginConfig(appCode);
    }

    // Global logout, the only logout semantic of the platform
    // Parameter request: LogoutRequest
    // Parameter httpRequest: HttpServletRequest
    // Parameter httpResponse: HttpServletResponse
    // Returns: LogoutResponse
    @Operation(summary = "退出登录")
    @PostMapping(path = IamAuthApiPaths.LOGOUT)
    public LogoutResponse logout(@RequestBody(required = false) LogoutRequest request,
                                 HttpServletRequest httpRequest,
                                 HttpServletResponse httpResponse) {
        String cookieSid = cookieService.resolveSid(httpRequest);
        LogoutResponse response = authenticationService.logout(request, cookieSid,
                IamRequestUtils.clientIp(httpRequest), IamRequestUtils.userAgent(httpRequest));
        cookieService.clearSessionCookie(httpResponse);
        return response;
    }
}