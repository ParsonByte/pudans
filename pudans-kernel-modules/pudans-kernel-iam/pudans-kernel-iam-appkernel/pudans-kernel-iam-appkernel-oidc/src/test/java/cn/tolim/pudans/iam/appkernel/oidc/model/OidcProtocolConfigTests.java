package cn.tolim.pudans.iam.appkernel.oidc.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OidcProtocolConfigTests {

    @Test
    void shouldReturnEmptyCollectionsForUnsetValues() {
        OidcProtocolConfig config = OidcProtocolConfig.builder().build();

        assertTrue(config.getRedirectUris().isEmpty());
        assertTrue(config.getPostLogoutRedirectUris().isEmpty());
        assertTrue(config.getGrantScopes().isEmpty());
        assertTrue(config.getClientAuthMethods().isEmpty());
        assertTrue(config.getResponseTypes().isEmpty());
        assertTrue(config.getJwks().isEmpty());
    }

    @Test
    void shouldKeepConfiguredCollections() {
        OidcProtocolConfig config = OidcProtocolConfig.builder()
                .appId("app-1")
                .appCode("app-code")
                .redirectUris(Set.of("https://app.example.com/callback"))
                .postLogoutRedirectUris(Set.of("https://app.example.com/logout"))
                .grantScopes(Set.of("openid"))
                .clientAuthMethods(Set.of("client_secret_basic"))
                .responseTypes(Set.of("code"))
                .jwks(List.of())
                .configured(true)
                .build();

        assertEquals("app-1", config.getAppId());
        assertEquals("app-code", config.getAppCode());
        assertEquals(Set.of("https://app.example.com/callback"), config.getRedirectUris());
        assertEquals(Set.of("https://app.example.com/logout"),
                config.getPostLogoutRedirectUris());
        assertEquals(Set.of("openid"), config.getGrantScopes());
        assertEquals(Set.of("client_secret_basic"), config.getClientAuthMethods());
        assertEquals(Set.of("code"), config.getResponseTypes());
        assertEquals(true, config.getConfigured());
    }
}