package cn.tolim.pudans.iam.protocol.oidc.authorization.client;

import cn.tolim.pudans.iam.communal.entity.app.AppEntity;
import cn.tolim.pudans.iam.communal.entity.app.AppOidcConfigEntity;
import cn.tolim.pudans.iam.communal.entity.app.po.AppOidcConfigPO;
import cn.tolim.pudans.iam.communal.repository.app.AppOidcConfigRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jose.jws.JwsAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


public class OidcConfigRegisteredClientRepository implements RegisteredClientRepository {

    private final AppOidcConfigRepository appOidcConfigRepository;
    private final AppRepository appRepository;

    public OidcConfigRegisteredClientRepository(AppOidcConfigRepository appOidcConfigRepository,
                                                AppRepository appRepository) {
        this.appOidcConfigRepository = appOidcConfigRepository;
        this.appRepository = appRepository;
    }

    // Persists the registered client to its IAM application and OIDC configuration.
    @Override
    public void save(RegisteredClient registeredClient) {
        Assert.notNull(registeredClient, "registeredClient 不能为空");
        String appId = registeredClient.getId();
        Assert.hasText(appId, "注册客户端 ID 不能为空");
        AppEntity app = appRepository.findById(appId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "IAM 应用不存在：" + appId));
        AppOidcConfigEntity config = appOidcConfigRepository.findByAppId(appId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "应用不存在 OIDC 配置：" + appId));
        // Update application client credentials
        app.setClientId(registeredClient.getClientId());
        app.setClientSecret(registeredClient.getClientSecret());
        app.setConfigured(true);
        // Update OIDC protocol configuration
        config.setClientAuthMethods(registeredClient.getClientAuthenticationMethods().stream()
                .map(ClientAuthenticationMethod::getValue)
                .collect(Collectors.toSet()));
        config.setAuthGrantTypes(registeredClient.getAuthorizationGrantTypes().stream()
                .map(AuthorizationGrantType::getValue)
                .collect(Collectors.toSet()));
        config.setRedirectUris(Set.copyOf(registeredClient.getRedirectUris()));
        config.setPostLogoutRedirectUris(
                Set.copyOf(registeredClient.getPostLogoutRedirectUris()));
        config.setGrantScopes(Set.copyOf(registeredClient.getScopes()));
        ClientSettings clientSettings = registeredClient.getClientSettings();
        config.setRequireAuthConsent(clientSettings.isRequireAuthorizationConsent());
        config.setRequireProofKey(clientSettings.isRequireProofKey());
        JwsAlgorithm clientAuthSigningAlgorithm =
                clientSettings.getTokenEndpointAuthenticationSigningAlgorithm();
        if (clientAuthSigningAlgorithm != null) {
            config.setTokenEndpointAuthSigningAlgorithm(clientAuthSigningAlgorithm.getName());
        }
        TokenSettings tokenSettings = registeredClient.getTokenSettings();
        config.setAuthorizationCodeTimeToLive(tokenSettings.getAuthorizationCodeTimeToLive());
        config.setDeviceCodeTimeToLive(tokenSettings.getDeviceCodeTimeToLive());
        config.setRefreshTokenTimeToLive(tokenSettings.getRefreshTokenTimeToLive());
        config.setAccessTokenTimeToLive(tokenSettings.getAccessTokenTimeToLive());
        SignatureAlgorithm idTokenSignatureAlgorithm =
                tokenSettings.getIdTokenSignatureAlgorithm();
        if (idTokenSignatureAlgorithm != null) {
            config.setIdTokenSignatureAlgorithm(idTokenSignatureAlgorithm.getName());
        }
        OAuth2TokenFormat accessTokenFormat = tokenSettings.getAccessTokenFormat();
        if (accessTokenFormat != null) {
            config.setAccessTokenFormat(accessTokenFormat.getValue());
        }
        config.setReuseRefreshToken(tokenSettings.isReuseRefreshTokens());
        appOidcConfigRepository.save(config);
        appRepository.save(app);
    }

    // Returns the registered client with the given ID, or null if not found.
    @Override
    public RegisteredClient findById(String id) {
        // Convert the stored OIDC configuration
        AppOidcConfigPO config = appOidcConfigRepository.getByAppId(id);
        if (Objects.isNull(config)) {
            return null;
        }
        return getIamRegisteredClient(config);
    }

    // Returns the registered client with the given client ID, or null if not found.
    @Override
    public RegisteredClient findByClientId(String clientId) {
        // Convert the stored OIDC configuration
        AppOidcConfigPO config = appOidcConfigRepository.getByClientId(clientId);
        if (Objects.isNull(config)) {
            return null;
        }
        return getIamRegisteredClient(config);
    }

    // Converts stored OIDC configuration to a registered client.
    private RegisteredClient getIamRegisteredClient(AppOidcConfigPO config) {
        return RegisteredClient
                // ID
                .withId(String.valueOf(config.getAppId()))
                // Client Id
                .clientId(config.getClientId())
                // Set the time the client identifier was issued
                .clientIdIssuedAt(null)
                // Client Secret
                .clientSecret(config.getClientSecret())
                // Client secret expiration time
                .clientSecretExpiresAt(null)
                // Client authentication method
                .clientAuthenticationMethods(clientAuthenticationMethods -> {
                    for (String key : config.getClientAuthMethods()) {
                        clientAuthenticationMethods.add(new ClientAuthenticationMethod(key));
                    }
                })
                // Authorization grant type
                .authorizationGrantTypes(authorizationGrantTypes -> {
                    for (String key : config.getAuthGrantTypes()) {
                        authorizationGrantTypes.add(new AuthorizationGrantType(key));
                    }
                })
                // Redirect URI
                .redirectUris(strings -> {
                    if (!CollectionUtils.isEmpty(config.getRedirectUris())) {
                        strings.addAll(config.getRedirectUris());
                    }
                })
                // Logout redirect URI
                .postLogoutRedirectUris(strings -> {
                    if (!CollectionUtils.isEmpty(config.getPostLogoutRedirectUris())) {
                        strings.addAll(config.getPostLogoutRedirectUris());
                    }
                })
                // Scope
                .scopes(strings -> strings.addAll(config.getGrantScopes()))
                // Client settings
                .clientSettings(ClientSettings.builder()
                        // Consent required
                        .requireAuthorizationConsent(config.getRequireAuthConsent())
                        // PKCE
                        .requireProofKey(config.getRequireProofKey())
                        // Token endpoint authentication signing algorithm
                        .tokenEndpointAuthenticationSigningAlgorithm(
                                SignatureAlgorithm.from(config.getTokenEndpointAuthSigningAlgorithm()))
                        .build())
                // Token settings
                .tokenSettings(TokenSettings.builder()
                        // Authorization code lifetime
                        .authorizationCodeTimeToLive(config.getAuthorizationCodeTimeToLive())
                        // Device code lifetime
                        .deviceCodeTimeToLive(config.getDeviceCodeTimeToLive())
                        // Refresh token lifetime
                        .refreshTokenTimeToLive(config.getRefreshTokenTimeToLive())
                        // Access token lifetime
                        .accessTokenTimeToLive(config.getAccessTokenTimeToLive())
                        // ID Token signing algorithm
                        .idTokenSignatureAlgorithm(
                                SignatureAlgorithm.from(config.getIdTokenSignatureAlgorithm()))
                        // Set the token format for access tokens
                        .accessTokenFormat(new OAuth2TokenFormat(config.getAccessTokenFormat()))
                        // Reuse refresh token
                        .reuseRefreshTokens(config.getReuseRefreshToken()).build())
                .build();
    }

}
