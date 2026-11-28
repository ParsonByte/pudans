package cn.tolim.pudans.iam.portal.endpoint;

import cn.tolim.pudans.iam.infra.context.ServletContextService;
import cn.tolim.pudans.iam.infra.result.ApiRestResult;
import cn.tolim.pudans.iam.portal.constant.PortalConstants;
import cn.tolim.pudans.iam.portal.dto.PortalAppsResponse;
import cn.tolim.pudans.iam.portal.dto.PortalLoginConfigResponse;
import cn.tolim.pudans.iam.portal.dto.PortalLogoutUrlResponse;
import cn.tolim.pudans.iam.portal.dto.PortalMeResponse;
import cn.tolim.pudans.iam.portal.service.PortalAppService;
import cn.tolim.pudans.iam.portal.service.PortalLoginConfigService;
import cn.tolim.pudans.iam.portal.service.PortalMeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@Tag(name = "IAM 门户")
@RestController
@RequestMapping(value = PortalConstants.PORTAL_API_PATH,
        produces = MediaType.APPLICATION_JSON_VALUE)
public class PortalEndpoint {

    private final PortalMeService portalMeService;
    private final PortalAppService portalAppService;
    private final PortalLoginConfigService portalLoginConfigService;

    public PortalEndpoint(PortalMeService portalMeService, PortalAppService portalAppService,
                          PortalLoginConfigService portalLoginConfigService) {
        this.portalMeService = portalMeService;
        this.portalAppService = portalAppService;
        this.portalLoginConfigService = portalLoginConfigService;
    }

    // Current user and global login session information
    // Parameter request: HttpServletRequest current request
    // Returns: ApiRestResult<PortalMeResponse>
    @Operation(summary = "获取当前用户与会话信息")
    @GetMapping("/me")
    public ApiRestResult<PortalMeResponse> me(HttpServletRequest request) {
        return ApiRestResult.ok(portalMeService.getCurrentUser(request));
    }

    // Application links the current user may access
    // Parameter request: HttpServletRequest current request
    // Returns: ApiRestResult<PortalAppsResponse>
    @Operation(summary = "查询已授权应用链接")
    @GetMapping("/apps")
    public ApiRestResult<PortalAppsResponse> apps(HttpServletRequest request) {
        return ApiRestResult.ok(portalAppService.getAuthorizedApps(request));
    }

    // Dedicated login page configuration
    // Parameter request: HttpServletRequest current request
    // Returns: ApiRestResult<PortalLoginConfigResponse>
    @Operation(summary = "获取独立登录页配置")
    @GetMapping("/login-config")
    public ApiRestResult<PortalLoginConfigResponse> loginConfig(HttpServletRequest request) {
        return ApiRestResult.ok(portalLoginConfigService.getLoginConfig(
                ServletContextService.getServerUrl(request)));
    }

    // Global logout URL information
    // Parameter request: HttpServletRequest current request
    // Returns: ApiRestResult<PortalLogoutUrlResponse>
    @Operation(summary = "获取全局退出地址")
    @GetMapping("/logout-url")
    public ApiRestResult<PortalLogoutUrlResponse> logoutUrl(HttpServletRequest request) {
        return ApiRestResult.ok(portalLoginConfigService.getLogoutUrl(
                ServletContextService.getServerUrl(request)));
    }
}