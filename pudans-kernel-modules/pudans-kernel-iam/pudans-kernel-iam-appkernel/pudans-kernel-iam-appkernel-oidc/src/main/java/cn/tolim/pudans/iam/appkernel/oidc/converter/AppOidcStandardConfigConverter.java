package cn.tolim.pudans.iam.appkernel.oidc.converter;

import cn.tolim.pudans.iam.appkernel.oidc.pojo.AppOidcProtocolEndpoint;
import cn.tolim.pudans.iam.appkernel.oidc.pojo.AppOidcStandardConfigGetResult;
import cn.tolim.pudans.iam.appkernel.oidc.pojo.AppOidcStandardSaveConfigParam;
import cn.tolim.pudans.iam.communal.constant.ProtocolConstants;
import cn.tolim.pudans.iam.communal.entity.app.AppOidcConfigEntity;
import cn.tolim.pudans.iam.communal.entity.app.po.AppOidcConfigPO;
import cn.tolim.pudans.iam.core.context.ContextService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.text.StringSubstitutor;
import org.mapstruct.Mapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;

import static cn.tolim.pudans.iam.communal.constant.ProtocolConstants.APP_CODE;
import static cn.tolim.pudans.iam.communal.constant.ProtocolConstants.OidcEndpointConstants.OIDC_AUTHORIZE_PATH;
import static cn.tolim.pudans.iam.communal.constant.ProtocolConstants.OidcEndpointConstants.WELL_KNOWN_OPENID_CONFIGURATION;


@Mapper(componentModel = "spring")
public interface AppOidcStandardConfigConverter {
    private static String formatUrl(String url) {
        return url == null ? null : url.replaceAll("/{2,}", "/").replace(":/", "://");
    }

    // Convert entity to OIDC configuration result
    default AppOidcStandardConfigGetResult entityConverterToOidcConfigResult(AppOidcConfigPO config) {
        AppOidcStandardConfigGetResult result = new AppOidcStandardConfigGetResult();
        if (Objects.isNull(config)) {
            return result;
        }
        // Protocol endpoint domain
        result.setProtocolEndpoint(getProtocolEndpointDomain(config.getAppCode()));
        // Authorization grant type
        result.setAuthGrantTypes(config.getAuthGrantTypes());
        // Login redirect URI
        result.setRedirectUris(config.getRedirectUris());
        // Logout redirect URI
        result.setPostLogoutRedirectUris(config.getPostLogoutRedirectUris());
        // Scope
        result.setGrantScopes(config.getGrantScopes());
        // Enable PKCE
        result.setRequireProofKey(config.getRequireProofKey());
        // Access token lifetime
        result.setAccessTokenTimeToLive(
                String.valueOf(config.getAccessTokenTimeToLive().toMinutes()));
        // Refresh token lifetime
        result.setRefreshTokenTimeToLive(
                String.valueOf(config.getRefreshTokenTimeToLive().toMinutes()));
        // ID token lifetime
        result.setIdTokenTimeToLive(String.valueOf(config.getIdTokenTimeToLive().toMinutes()));
        // Device authorization code lifetime
        result
                .setDeviceCodeTimeToLive(String.valueOf(config.getDeviceCodeTimeToLive().toMinutes()));
        // access_token format
        result.setAccessTokenFormat(config.getAccessTokenFormat());
        // Authorization code lifetime
        result.setAuthorizationCodeTimeToLive(
                String.valueOf(config.getAuthorizationCodeTimeToLive().toMinutes()));
        // ID token lifetime
        result.setReuseRefreshToken(config.getReuseRefreshToken());
        // ID token lifetime
        result.setIdTokenTimeToLive(String.valueOf(config.getIdTokenTimeToLive().toMinutes()));
        // ID token signing algorithm
        result.setIdTokenSignatureAlgorithm(config.getIdTokenSignatureAlgorithm());
        // Login initiation address
        result.setInitLoginUrl(config.getInitLoginUrl());
        // Grant type
        result.setAuthorizationType(config.getAuthorizationType());
        return result;
    }

    // Convert save model to entity
    default AppOidcConfigEntity appOidcStandardSaveConfigParamToEntity(AppOidcStandardSaveConfigParam config) {
        if (config == null) {
            return null;
        }
        AppOidcConfigEntity entity = new AppOidcConfigEntity();
        if (CollectionUtils.isNotEmpty(config.getClientAuthMethods())) {
            entity.setClientAuthMethods(new LinkedHashSet<>(config.getClientAuthMethods()));
        }
        if (CollectionUtils.isNotEmpty(config.getAuthGrantTypes())) {
            entity.setAuthGrantTypes(new LinkedHashSet<>(config.getAuthGrantTypes()));
        }
        if (CollectionUtils.isNotEmpty(config.getRedirectUris())) {
            entity.setRedirectUris(new LinkedHashSet<>(config.getRedirectUris()));
        }
        if (CollectionUtils.isNotEmpty(config.getPostLogoutRedirectUris())) {
            entity
                    .setPostLogoutRedirectUris(new LinkedHashSet<>(config.getPostLogoutRedirectUris()));
        }
        if (CollectionUtils.isNotEmpty(config.getGrantScopes())) {
            entity.setGrantScopes(new LinkedHashSet<>(config.getGrantScopes()));
        }
        entity.setRequireAuthConsent(config.getRequireAuthConsent());
        entity.setRequireProofKey(config.getRequireProofKey());
        entity.setTokenEndpointAuthSigningAlgorithm(config.getTokenEndpointAuthSigningAlgorithm());
        if (config.getRefreshTokenTimeToLive() != null) {
            entity
                    .setRefreshTokenTimeToLive(Duration.ofMinutes(config.getRefreshTokenTimeToLive()));
        }
        if (config.getAuthorizationCodeTimeToLive() != null) {
            entity.setAuthorizationCodeTimeToLive(
                    Duration.ofMinutes(config.getAuthorizationCodeTimeToLive()));
        }
        if (config.getDeviceCodeTimeToLive() != null) {
            entity.setDeviceCodeTimeToLive(Duration.ofMinutes(config.getDeviceCodeTimeToLive()));
        }
        if (config.getIdTokenTimeToLive() != null) {
            entity.setIdTokenTimeToLive(Duration.ofMinutes(config.getIdTokenTimeToLive()));
        }
        if (config.getAccessTokenTimeToLive() != null) {
            entity.setAccessTokenTimeToLive(Duration.ofMinutes(config.getAccessTokenTimeToLive()));
        }
        entity.setIdTokenSignatureAlgorithm(config.getIdTokenSignatureAlgorithm());
        entity.setAccessTokenFormat(config.getAccessTokenFormat());
        entity.setReuseRefreshToken(config.getReuseRefreshToken());
        return entity;
    }

    // Get protocol endpoint
    private AppOidcProtocolEndpoint getProtocolEndpointDomain(String appCode) {
        AppOidcProtocolEndpoint domain = new AppOidcProtocolEndpoint();
        // issues
        Map<String,String> variables = new HashMap<>(16);
        variables.put(APP_CODE,appCode);
        StringSubstitutor sub = new StringSubstitutor(variables, "{", "}");
        // Issuer
        domain.setIssuer(sub.replace(ContextService.getPortalPublicBaseUrl() + OIDC_AUTHORIZE_PATH));
        // Discovery endpoint
        domain.setDiscoveryEndpoint(formatUrl(ContextService.getPortalPublicBaseUrl() + sub.replace(WELL_KNOWN_OPENID_CONFIGURATION)));
        // Authentication endpoint
        domain.setAuthorizationEndpoint(formatUrl(ContextService.getPortalPublicBaseUrl() +  sub.replace(ProtocolConstants.OidcEndpointConstants.AUTHORIZATION_ENDPOINT)));
        // Token endpoint
        domain.setTokenEndpoint(formatUrl(ContextService.getPortalPublicBaseUrl() + sub.replace( ProtocolConstants.OidcEndpointConstants.TOKEN_ENDPOINT)));
        // JWKS endpoint
        domain.setJwksEndpoint(formatUrl(ContextService.getPortalPublicBaseUrl() +  sub.replace(ProtocolConstants.OidcEndpointConstants.JWK_SET_ENDPOINT)));
        // Revocation endpoint
        domain.setRevokeEndpoint(formatUrl(ContextService.getPortalPublicBaseUrl()+  sub.replace(ProtocolConstants.OidcEndpointConstants.TOKEN_REVOCATION_ENDPOINT)));
        // UserInfo endpoint
        domain.setUserinfoEndpoint(formatUrl(ContextService.getPortalPublicBaseUrl() +  sub.replace(ProtocolConstants.OidcEndpointConstants.OIDC_USER_INFO_ENDPOINT)));
        // Logout endpoint
        domain.setEndSessionEndpoint(formatUrl(ContextService.getPortalPublicBaseUrl() +  sub.replace(ProtocolConstants.OidcEndpointConstants.OIDC_LOGOUT_ENDPOINT)));
        return domain;
    }
}
