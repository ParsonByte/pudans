package cn.tolim.pudans.iam.appkernel.oidc.converter;

import cn.tolim.pudans.iam.appkernel.oidc.pojo.AppOidcStandardConfigGetResult;
import cn.tolim.pudans.iam.appkernel.oidc.pojo.AppOidcStandardSaveConfigParam;
import cn.tolim.pudans.iam.communal.entity.app.AppOidcConfigEntity;
import cn.tolim.pudans.iam.communal.entity.app.po.AppOidcConfigPO;
import cn.tolim.pudans.iam.communal.enums.app.AuthorizationType;
import cn.tolim.pudans.iam.core.context.ContextService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppOidcStandardConfigConverterTests {

    private static final String BASE_URL = "https://iam.example.com/";

    private final AppOidcStandardConfigConverter converter =
            new AppOidcStandardConfigConverter() {
            };

    private static AppOidcConfigEntity configEntity() {
        AppOidcConfigEntity entity = new AppOidcConfigEntity();
        entity.setAppId("app-1");
        entity.setClientAuthMethods(new LinkedHashSet<>(List.of("client_secret_basic")));
        entity.setAuthGrantTypes(new LinkedHashSet<>(List.of("authorization_code")));
        entity.setResponseTypes(new LinkedHashSet<>(List.of("code")));
        entity.setRedirectUris(new LinkedHashSet<>(List.of("https://app.example.com/callback")));
        entity.setPostLogoutRedirectUris(new LinkedHashSet<>(List.of("https://app.example.com/logout")));
        entity.setGrantScopes(new LinkedHashSet<>(List.of("openid", "profile")));
        entity.setRequireAuthConsent(Boolean.FALSE);
        entity.setRequireProofKey(Boolean.TRUE);
        entity.setTokenEndpointAuthSigningAlgorithm("RS256");
        entity.setAccessTokenTimeToLive(Duration.ofMinutes(20));
        entity.setRefreshTokenTimeToLive(Duration.ofMinutes(43200));
        entity.setAuthorizationCodeTimeToLive(Duration.ofMinutes(5));
        entity.setDeviceCodeTimeToLive(Duration.ofMinutes(5));
        entity.setIdTokenTimeToLive(Duration.ofMinutes(30));
        entity.setIdTokenSignatureAlgorithm("RS256");
        entity.setAccessTokenFormat("reference");
        entity.setReuseRefreshToken(Boolean.FALSE);
        return entity;
    }

    @Test
    void shouldReturnEmptyResultForMissingConfiguration() {
        AppOidcStandardConfigGetResult result = converter.entityConverterToOidcConfigResult(null);

        assertNotNull(result);
        assertNull(result.getAuthGrantTypes());
        assertNull(result.getProtocolEndpoint());
    }

    @Test
    void shouldConvertProjectionToConfigResultWithMinuteLifetimes() {
        AppOidcConfigPO po = new AppOidcConfigPO(configEntity(), "app-code", "Order", "oidc",
                "client-1", "secret-1", "https://iam.example.com/init",
                AuthorizationType.AUTHORIZATION, true, true);

        try (MockedStatic<ContextService> context = Mockito.mockStatic(ContextService.class)) {
            context.when(ContextService::getPortalPublicBaseUrl).thenReturn(BASE_URL);

            AppOidcStandardConfigGetResult result =
                    converter.entityConverterToOidcConfigResult(po);

            assertEquals("https://iam.example.com/init", result.getInitLoginUrl());
            assertEquals(Set.of("authorization_code"), result.getAuthGrantTypes());
            assertEquals(Set.of("openid", "profile"), result.getGrantScopes());
            assertEquals(Set.of("https://app.example.com/callback"), result.getRedirectUris());
            assertEquals(Boolean.TRUE, result.getRequireProofKey());
            assertEquals("20", result.getAccessTokenTimeToLive());
            assertEquals("43200", result.getRefreshTokenTimeToLive());
            assertEquals("5", result.getAuthorizationCodeTimeToLive());
            assertEquals("5", result.getDeviceCodeTimeToLive());
            assertEquals("30", result.getIdTokenTimeToLive());
            assertEquals("RS256", result.getIdTokenSignatureAlgorithm());
            assertEquals("reference", result.getAccessTokenFormat());
            assertEquals(AuthorizationType.AUTHORIZATION, result.getAuthorizationType());
            assertNotNull(result.getProtocolEndpoint());
            assertTrue(result.getProtocolEndpoint().getIssuer().startsWith(BASE_URL));
            assertTrue(result.getProtocolEndpoint().getIssuer().contains("/oidc"));
            assertTrue(result.getProtocolEndpoint().getAuthorizationEndpoint().contains("app-code"));
            assertTrue(!result.getProtocolEndpoint().getDiscoveryEndpoint().contains("//apis"));
        }
    }

    @Test
    void shouldConvertSaveParamToEntity() {
        AppOidcStandardSaveConfigParam param = new AppOidcStandardSaveConfigParam();
        param.setClientAuthMethods(List.of("client_secret_basic", "none"));
        param.setAuthGrantTypes(List.of("authorization_code", "refresh_token"));
        param.setRedirectUris(List.of("https://app.example.com/callback"));
        param.setPostLogoutRedirectUris(List.of("https://app.example.com/logout"));
        param.setGrantScopes(List.of("openid"));
        param.setRequireAuthConsent(Boolean.TRUE);
        param.setRequireProofKey(Boolean.TRUE);
        param.setTokenEndpointAuthSigningAlgorithm("RS256");
        param.setAccessTokenTimeToLive(20L);
        param.setRefreshTokenTimeToLive(43200L);
        param.setAuthorizationCodeTimeToLive(5L);
        param.setDeviceCodeTimeToLive(6L);
        param.setIdTokenTimeToLive(30L);
        param.setIdTokenSignatureAlgorithm("RS256");
        param.setAccessTokenFormat("reference");
        param.setReuseRefreshToken(Boolean.FALSE);

        AppOidcConfigEntity entity = converter.appOidcStandardSaveConfigParamToEntity(param);

        assertNotNull(entity);
        assertEquals(Set.of("client_secret_basic", "none"), entity.getClientAuthMethods());
        assertEquals(Set.of("authorization_code", "refresh_token"), entity.getAuthGrantTypes());
        assertEquals(Set.of("https://app.example.com/callback"), entity.getRedirectUris());
        assertEquals(Set.of("https://app.example.com/logout"), entity.getPostLogoutRedirectUris());
        assertEquals(Set.of("openid"), entity.getGrantScopes());
        assertEquals(Boolean.TRUE, entity.getRequireAuthConsent());
        assertEquals(Boolean.TRUE, entity.getRequireProofKey());
        assertEquals(Duration.ofMinutes(20), entity.getAccessTokenTimeToLive());
        assertEquals(Duration.ofMinutes(43200), entity.getRefreshTokenTimeToLive());
        assertEquals(Duration.ofMinutes(5), entity.getAuthorizationCodeTimeToLive());
        assertEquals(Duration.ofMinutes(6), entity.getDeviceCodeTimeToLive());
        assertEquals(Duration.ofMinutes(30), entity.getIdTokenTimeToLive());
        assertEquals("RS256", entity.getIdTokenSignatureAlgorithm());
        assertEquals("reference", entity.getAccessTokenFormat());
        assertEquals(Boolean.FALSE, entity.getReuseRefreshToken());
    }

    @Test
    void shouldReturnNullForNullSaveParam() {
        assertNull(converter.appOidcStandardSaveConfigParamToEntity(null));
    }
}