package cn.tolim.pudans.iam.endpoint;

import cn.tolim.pudans.contract.iam.dto.app.MyApplicationSummary;
import cn.tolim.pudans.contract.iam.dto.session.SessionRevokeRequest;
import cn.tolim.pudans.contract.iam.dto.session.SessionRevokeResponse;
import cn.tolim.pudans.contract.iam.dto.session.SessionRevokeUserRequest;
import cn.tolim.pudans.contract.iam.dto.session.SessionRevokeUserResponse;
import cn.tolim.pudans.contract.iam.dto.session.SessionSummary;
import cn.tolim.pudans.contract.iam.dto.session.SessionTouchRequest;
import cn.tolim.pudans.contract.iam.dto.session.SessionTouchResponse;
import cn.tolim.pudans.contract.iam.dto.session.SessionValidateRequest;
import cn.tolim.pudans.contract.iam.dto.session.SessionValidateResponse;
import cn.tolim.pudans.contract.iam.dto.ticket.UserInfo;
import cn.tolim.pudans.iam.contract.IamAuthApiPaths;
import cn.tolim.pudans.iam.communal.session.LoginSessionEntity;
import cn.tolim.pudans.iam.core.auth.AuthenticationService;
import cn.tolim.pudans.iam.core.auth.exception.SessionNotActiveException;
import cn.tolim.pudans.iam.core.auth.session.SessionManagementService;
import cn.tolim.pudans.iam.core.auth.user.ApplicationAccessService;
import cn.tolim.pudans.iam.security.IamRequestUtils;
import cn.tolim.pudans.iam.security.IamSessionCookieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "IAM 会话管理")
@RestController
public class IamSessionEndpoint {

    private final AuthenticationService authenticationService;
    private final SessionManagementService sessionManagementService;
    private final ApplicationAccessService applicationAccessService;
    private final IamSessionCookieService cookieService;

    public IamSessionEndpoint(AuthenticationService authenticationService,
                              SessionManagementService sessionManagementService,
                              ApplicationAccessService applicationAccessService,
                              IamSessionCookieService cookieService) {
        this.authenticationService = authenticationService;
        this.sessionManagementService = sessionManagementService;
        this.applicationAccessService = applicationAccessService;
        this.cookieService = cookieService;
    }

    // Current user resolved from the global login session
    // Parameter request: HttpServletRequest
    // Returns: UserInfo
    @Operation(summary = "查询当前用户")
    @GetMapping(path = IamAuthApiPaths.ME)
    public UserInfo me(HttpServletRequest request) {
        return authenticationService.currentUser(requireSid(request));
    }

    // Applications the current user may open
    // Parameter request: HttpServletRequest
    // Returns: List
    @Operation(summary = "查询我的应用")
    @GetMapping(path = IamAuthApiPaths.ME_APPS)
    public List<MyApplicationSummary> myApplications(HttpServletRequest request) {
        LoginSessionEntity session = authenticationService.currentSession(requireSid(request));
        return applicationAccessService.listAccessibleApplications(session);
    }

    // Validates a global login session, used by applications and the gateway
    // Parameter request: SessionValidateRequest
    // Parameter httpRequest: HttpServletRequest
    // Returns: SessionValidateResponse
    @Operation(summary = "校验会话")
    @PostMapping(path = {IamAuthApiPaths.SESSION_VALIDATE, IamAuthApiPaths.SESSIONS_VALIDATE})
    public SessionValidateResponse validate(@RequestBody SessionValidateRequest request,
                                            HttpServletRequest httpRequest) {
        return sessionManagementService.validate(request, IamRequestUtils.clientIp(httpRequest));
    }

    // Touches a global login session
    // Parameter request: SessionTouchRequest
    // Returns: SessionTouchResponse
    @Operation(summary = "续期会话")
    @PostMapping(path = IamAuthApiPaths.SESSION_TOUCH)
    public SessionTouchResponse touch(@RequestBody SessionTouchRequest request) {
        return sessionManagementService.touch(request == null ? null : request.getSid());
    }

    // Revokes one global login session, internal or administrator call
    // Parameter request: SessionRevokeRequest
    // Returns: SessionRevokeResponse
    @Operation(summary = "撤销会话")
    @PostMapping(path = IamAuthApiPaths.SESSION_REVOKE)
    public SessionRevokeResponse revoke(@RequestBody SessionRevokeRequest request) {
        return sessionManagementService.revoke(request);
    }

    // Revokes every global login session of a user, internal or administrator call
    // Parameter request: SessionRevokeUserRequest
    // Returns: SessionRevokeUserResponse
    @Operation(summary = "撤销用户会话")
    @PostMapping(path = IamAuthApiPaths.SESSION_REVOKE_USER)
    public SessionRevokeUserResponse revokeUser(@RequestBody SessionRevokeUserRequest request) {
        return sessionManagementService.revokeUser(request);
    }

    // Global login session detail including application session caches
    // Parameter sid: String
    // Returns: SessionSummary
    @Operation(summary = "查询会话详情")
    @GetMapping(path = IamAuthApiPaths.SESSION_DETAIL)
    public SessionSummary session(@PathVariable("sid") String sid) {
        return sessionManagementService.getSession(sid);
    }

    // Global login sessions of a user
    // Parameter userId: String
    // Returns: List
    @Operation(summary = "查询用户会话")
    @GetMapping(path = IamAuthApiPaths.SESSION_USER)
    public List<SessionSummary> userSessions(@PathVariable("userId") String userId) {
        return sessionManagementService.listUserSessions(userId);
    }

    private String requireSid(HttpServletRequest request) {
        String sid = cookieService.resolveSid(request);
        if (sid == null || sid.isBlank()) {
            throw new SessionNotActiveException("Global login session id is missing");
        }
        return sid;
    }
}