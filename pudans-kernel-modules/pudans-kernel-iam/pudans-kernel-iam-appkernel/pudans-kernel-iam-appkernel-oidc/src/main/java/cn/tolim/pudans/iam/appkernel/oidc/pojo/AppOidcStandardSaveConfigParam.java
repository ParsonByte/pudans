package cn.tolim.pudans.iam.appkernel.oidc.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;


@Data
@Schema(description = "保存 OIDC 应用配置参数")
public class AppOidcStandardSaveConfigParam implements Serializable {

    @Serial
    private static final long serialVersionUID = 7257798528680745281L;

    // Grant type
    @NotNull(message = "授权类型不能为空")
    @Schema(description = "授权类型")
    private List<@NotBlank(message = "授权类型不能为空") String> authGrantTypes;

    // Login redirect URI
    @NotNull(message = "登录重定向URI不能为空")
    @Schema(description = "登录重定向URI")
    private List<@NotBlank(message = "登录重定向URI不能为空") @URL(message = "登录重定向URI格式不正确") String> redirectUris;

    // Logout redirect URI
    @Schema(description = "登出重定向URI")
    private List<@NotBlank(message = "登出重定向URI不能为空") @URL(message = "登出重定向URI格式不正确") String> postLogoutRedirectUris;

    // Login initiation URL
    @Schema(description = "登录发起登录URL")
    private String initLoginUrl;

    // Grant scope
    @NotNull(message = "授予范围不能为空")
    @Schema(description = "授予范围")
    private List<String> grantScopes;

    // Client authentication method
    @Schema(description = "客户端身份验证方法")
    private List<String> clientAuthMethods;

    // PKCE required
    @Schema(description = "是否需要PKCE")
    private Boolean requireProofKey = false;

    // Access Token lifetime
    @NotNull(message = "Access Token 生存时间不能为空")
    @Schema(description = "访问令牌生存时间")
    private Long accessTokenTimeToLive;

    // Access Token format
    @Schema(description = "访问令牌格式")
    private String accessTokenFormat;

    // Refresh Token lifetime
    @NotNull(message = "Refresh Token 生存时间不能为空")
    @Schema(description = "刷新令牌生存时间")
    private Long refreshTokenTimeToLive;

    // Authorization code lifetime in authorization code mode
    @NotNull(message = "授权码模式授权码生存时间不能为空")
    @Schema(description = "授权码模式授权码生存时间")
    private Long authorizationCodeTimeToLive;

    // Device code lifetime in device mode
    @Schema(description = "设备模式授权码生存时间")
    private Long deviceCodeTimeToLive;

    // ID Token lifetime
    @NotNull(message = "Id Token 生存时间不能为空")
    @Schema(description = "ID 令牌生存时间")
    private Long idTokenTimeToLive;

    // ID Token signing algorithm
    @NotBlank(message = "ID Token签名算法不能为空")
    @Schema(description = "ID 令牌签名算法")
    private String idTokenSignatureAlgorithm;

    // Token signing algorithm
    @Schema(description = "令牌端点身份验证签名算法")
    private String tokenEndpointAuthSigningAlgorithm;

    // Reuse refresh token
    @Schema(description = "重用刷新令牌")
    private Boolean reuseRefreshToken;

    // Authorization consent required
    @Schema(description = "需要授权同意")
    private Boolean requireAuthConsent;

}
