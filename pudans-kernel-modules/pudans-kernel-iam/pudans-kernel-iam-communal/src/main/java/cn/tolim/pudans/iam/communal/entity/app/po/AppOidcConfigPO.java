package cn.tolim.pudans.iam.communal.entity.app.po;

import cn.tolim.pudans.iam.communal.entity.app.AppOidcConfigEntity;
import cn.tolim.pudans.iam.communal.enums.app.AuthorizationType;
import lombok.Data;
import lombok.EqualsAndHashCode;


@Data
@EqualsAndHashCode(callSuper = true)
public class AppOidcConfigPO extends AppOidcConfigEntity {

    // Application code
    private String appCode;

    // Application name
    private String appName;

    // Template
    private String appTemplate;

    // Client ID
    private String clientId;

    // Client secret
    private String clientSecret;

    // SSO login link
    private String initLoginUrl;

    // Scope
    private AuthorizationType authorizationType;

    // Application enabled
    private Boolean enabled;

    // Configured
    private Boolean configured;

    public AppOidcConfigPO(AppOidcConfigEntity config, String appCode, String appName,
                           String appTemplate, String clientId, String clientSecret,
                           String initLoginUrl, AuthorizationType authorizationType,
                           Boolean enabled, Boolean configured) {
        // DTO field
        this.appCode = appCode;
        this.appName = appName;
        this.appTemplate = appTemplate;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.initLoginUrl = initLoginUrl;
        this.authorizationType = authorizationType;
        this.enabled = enabled;
        this.configured = configured;
        // OIDC configuration field
        super.setAppId(config.getAppId());
        super.setClientAuthMethods(config.getClientAuthMethods());
        super.setAuthGrantTypes(config.getAuthGrantTypes());
        super.setResponseTypes(config.getResponseTypes());
        super.setRedirectUris(config.getRedirectUris());
        super.setPostLogoutRedirectUris(config.getPostLogoutRedirectUris());
        super.setGrantScopes(config.getGrantScopes());
        super.setRequireAuthConsent(config.getRequireAuthConsent());
        super.setRequireProofKey(config.getRequireProofKey());
        super.setTokenEndpointAuthSigningAlgorithm(config.getTokenEndpointAuthSigningAlgorithm());
        super.setRefreshTokenTimeToLive(config.getRefreshTokenTimeToLive());
        super.setAuthorizationCodeTimeToLive(config.getAuthorizationCodeTimeToLive());
        super.setDeviceCodeTimeToLive(config.getDeviceCodeTimeToLive());
        super.setIdTokenTimeToLive(config.getIdTokenTimeToLive());
        super.setIdTokenSignatureAlgorithm(config.getIdTokenSignatureAlgorithm());
        super.setAccessTokenFormat(config.getAccessTokenFormat());
        super.setAccessTokenTimeToLive(config.getAccessTokenTimeToLive());
        super.setReuseRefreshToken(config.getReuseRefreshToken());
        super.setCreateBy(config.getCreateBy());
        super.setCreateTime(config.getCreateTime());
        super.setUpdateBy(config.getUpdateBy());
        super.setUpdateTime(config.getUpdateTime());
        super.setRemark(config.getRemark());
    }
}
