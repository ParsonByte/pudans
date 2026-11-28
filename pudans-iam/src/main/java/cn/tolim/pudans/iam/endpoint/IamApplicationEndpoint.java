package cn.tolim.pudans.iam.endpoint;

import cn.tolim.pudans.contract.iam.dto.app.ApplicationAccessResponse;
import cn.tolim.pudans.contract.iam.dto.app.ApplicationSummary;
import cn.tolim.pudans.iam.communal.entity.app.AppEntity;
import cn.tolim.pudans.iam.communal.repository.app.AppJwtConfigRepository;
import cn.tolim.pudans.iam.communal.session.LoginSessionEntity;
import cn.tolim.pudans.iam.contract.IamAuthApiPaths;
import cn.tolim.pudans.iam.core.auth.AuthenticationService;
import cn.tolim.pudans.iam.core.auth.IamAuthProperties;
import cn.tolim.pudans.iam.core.auth.exception.SessionNotActiveException;
import cn.tolim.pudans.iam.core.auth.user.ApplicationAccessService;
import cn.tolim.pudans.iam.security.IamSessionCookieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "IAM 应用管理")
@RestController
public class IamApplicationEndpoint {

    private final ApplicationAccessService applicationAccessService;
    private final AuthenticationService authenticationService;
    private final AppJwtConfigRepository appJwtConfigRepository;
    private final IamAuthProperties authProperties;
    private final IamSessionCookieService cookieService;

    public IamApplicationEndpoint(ApplicationAccessService applicationAccessService,
                                  AuthenticationService authenticationService,
                                  AppJwtConfigRepository appJwtConfigRepository,
                                  IamAuthProperties authProperties,
                                  IamSessionCookieService cookieService) {
        this.applicationAccessService = applicationAccessService;
        this.authenticationService = authenticationService;
        this.appJwtConfigRepository = appJwtConfigRepository;
        this.authProperties = authProperties;
        this.cookieService = cookieService;
    }

    // Checks whether a principal may access an application
    // Parameter appCode: String
    // Parameter userId: String
    // Parameter orgId: String
    // Parameter userType: String
    // Parameter request: HttpServletRequest
    // Returns: ApplicationAccessResponse
    @Operation(summary = "获取应用访问权限")
    @GetMapping(path = IamAuthApiPaths.APP_ACCESS)
    public ApplicationAccessResponse access(@PathVariable("appCode") String appCode,
                                            @RequestParam(value = "userId", required = false) String userId,
                                            @RequestParam(value = "orgId", required = false) String orgId,
                                            @RequestParam(value = "userType", required = false) String userType,
                                            HttpServletRequest request) {
        if (userId == null || userId.isBlank()) {
            String sid = cookieService.resolveSid(request);
            if (sid == null || sid.isBlank()) {
                throw new SessionNotActiveException("userId or a global login session is required");
            }
            LoginSessionEntity session = authenticationService.currentSession(sid);
            userId = session.getUserId();
            orgId = session.getOrgId();
            userType = session.getUserType();
        }
        return applicationAccessService.checkAccess(userId, orgId, userType, appCode);
    }

    // Application registration detail
    // Parameter appCode: String
    // Returns: ApplicationSummary
    @Operation(summary = "获取应用详情")
    @GetMapping(path = IamAuthApiPaths.APP_DETAIL)
    public ApplicationSummary application(@PathVariable("appCode") String appCode) {
        AppEntity app = applicationAccessService.requireEnabledApp(appCode);
        String redirectUrl = appJwtConfigRepository.findByAppId(app.getId())
                .map(config -> config.getRedirectUrl())
                .orElse(null);
        return ApplicationSummary.builder()
                .appCode(app.getCode())
                .name(app.getName())
                .baseUrl(app.getInitLoginUrl())
                .redirectUrl(redirectUrl)
                .backchannelLogoutUri(authProperties.getBackchannel().getAppUrls().get(app.getCode()))
                .protocol(app.getProtocol() == null ? null : app.getProtocol().getCode())
                .type(app.getType() == null ? null : app.getType().getCode())
                .icon(app.getIcon())
                .enabled(app.getEnabled())
                .configured(app.getConfigured())
                .ticketTimeToLiveSeconds(authProperties.getTicket().getTtl().toSeconds())
                .accessPolicyMode(cn.tolim.pudans.iam.communal.enums.app.AuthorizationType.ALL_ACCESS.equals(app.getAuthorizationType()) ? "ALL_ACCESS" : "DEFAULT_DENY")
                .build();
    }
}