package cn.tolim.pudans.iam.protocol.oidc.authorization.client;

import cn.tolim.pudans.iam.appkernel.core.context.ApplicationContext;
import cn.tolim.pudans.iam.appkernel.core.context.ApplicationContextHolder;
import cn.tolim.pudans.iam.appkernel.oidc.model.OidcProtocolConfig;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.Objects;


public class OidcConfigRegisteredClientRepositoryWrapper implements RegisteredClientRepository {

    private final RegisteredClientRepository registeredClientRepository;

    public OidcConfigRegisteredClientRepositoryWrapper(RegisteredClientRepository registeredClientRepository) {
        Assert.notNull(registeredClientRepository, "registeredClientRepository 不能为空");
        this.registeredClientRepository = registeredClientRepository;
    }

    @Override
    public void save(RegisteredClient registeredClient) {
        registeredClientRepository.save(registeredClient);
    }

    @Override
    public RegisteredClient findById(String id) {
        ApplicationContext context = ApplicationContextHolder.getApplicationContext();
        String appId = context.getAppId();
        if (!String.valueOf(appId).equals(id)) {
            return null;
        }
        OidcProtocolConfig config = (OidcProtocolConfig) context.getConfig()
                .get(OidcProtocolConfig.class.getName());
        if (Objects.isNull(config)) {
            return registeredClientRepository.findById(id);
        }
        return getIamRegisteredClient(config);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        ApplicationContext context = ApplicationContextHolder.getApplicationContext();
        if (!String.valueOf(context.getClientId()).equals(clientId)) {
            return null;
        }
        OidcProtocolConfig config = (OidcProtocolConfig) context.getConfig()
                .get(OidcProtocolConfig.class.getName());
        if (Objects.isNull(config)) {
            return registeredClientRepository.findByClientId(clientId);
        }
        return getIamRegisteredClient(config);
    }

    private RegisteredClient getIamRegisteredClient(OidcProtocolConfig config) {
        return RegisteredClient
                // ID
                .withId(config.getAppId())
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
