package cn.tolim.pudans.iam.appkernel.oidc.pojo;

import cn.tolim.pudans.iam.communal.enums.app.AuthorizationType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;


@Data
@Schema(description = "OIDC 配置响应")
public class AppOidcStandardConfigGetResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 4177874005424703372L;

    // SSO login link
    @Schema(description = "SSO 登录链接")
    private String initLoginUrl;

    // Scope
    @Schema(description = "SSO 授权范围")
    private AuthorizationType authorizationType;

    // authorizationGrantTypes
    @Schema(description = "认证授权类型")
    private Set<String> authGrantTypes;

    // Client authentication method
    @Schema(description = "客户端认证方式")
    private Set<String> clientAuthMethods;

    // Redirect URI
    @Schema(description = "重定向URI")
    private Set<String> redirectUris;

    // Logout redirect URI
    @Schema(description = "登出重定向URI")
    private Set<String> postLogoutRedirectUris;
    // scopes
    @Schema(description = "授权范围")
    private Set<String> grantScopes;

    // Enable PKCE
    @Schema(description = "启用PKCE")
    private Boolean requireProofKey;

    // Token endpoint authentication signing algorithm
    @Schema(description = "令牌端点身份验证签名算法")
    private String tokenEndpointAuthSigningAlgorithm;

    // Requires authorization consent
    @Schema(description = "是否需要授权同意")
    private Boolean requireAuthConsent;

    // Access token lifetime
    @Schema(description = "访问令牌有效时间")
    private String accessTokenTimeToLive;

    // Refresh token lifetime
    @Schema(description = "刷新令牌有效时间")
    private String refreshTokenTimeToLive;

    // Authorization code lifetime (minutes)
    @Schema(description = "授权码 生存时间（分钟）")
    private String authorizationCodeTimeToLive;

    // Device code lifetime (minutes)
    @Schema(description = "设备CODE 生存时间（分钟）")
    private String deviceCodeTimeToLive;

    // ID token lifetime
    @Schema(description = "ID 令牌有效时间")
    private String idTokenTimeToLive;

    // ID token signing algorithm
    @Schema(description = "ID 令牌签名算法")
    private String idTokenSignatureAlgorithm;

    // Protocol endpoint domain
    @Schema(description = "协议端点域")
    private AppOidcProtocolEndpoint protocolEndpoint;

    // Access Token format
    @Schema(description = "访问令牌格式")
    private String accessTokenFormat;

    // Reuse refresh token
    @Schema(description = "是否重用刷新令牌")
    private Boolean reuseRefreshToken;
}
