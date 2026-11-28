package cn.tolim.pudans.iam.portal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "专用登录页配置")
public class PortalLoginConfigResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 8459035121956922327L;

    // Public homepage URL
    @Schema(description = "公开首页地址")
    private String homeUrl;

    // Public login page URL
    @Schema(description = "公开登录页地址")
    private String loginUrl;

    // Public global logout URL
    @Schema(description = "公开全局登出地址")
    private String logoutUrl;

    // Login page branding
    @Schema(description = "登录页品牌")
    private PortalBrandingView branding;

    // Login methods
    @Schema(description = "登录方式列表")
    private List<PortalLoginMethodView> methods = new ArrayList<>();

    // Social and enterprise identity providers
    @Schema(description = "身份提供商列表")
    private List<PortalIdentityProviderView> identityProviders = new ArrayList<>();

    // Whether a captcha is required
    @Schema(description = "是否需要验证码")
    private Boolean captchaEnabled;

    // Whether MFA is required after the first factor
    @Schema(description = "是否需要 MFA")
    private Boolean mfaRequired;

    // Whether remember-me is available
    @Schema(description = "是否支持记住我")
    private Boolean rememberMeEnabled;

    // Global login session lifetime in seconds
    @Schema(description = "全局登录会话有效期（秒）")
    private Long sessionValidTimeSeconds;
}