package cn.tolim.pudans.iam.protocol.oidc.authentication;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.log.LogMessage;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.util.Assert;

import java.security.Principal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static cn.tolim.pudans.iam.protocol.oidc.authentication.OAuth2AuthenticationProviderUtils.getAuthenticatedClientElseThrowInvalidClient;


public final class OAuth2RefreshTokenAuthenticationProvider implements AuthenticationProvider {
    private static final String ERROR_URI = "https://datatracker.ietf.org/doc/html/rfc6749#section-5.2";
    private static final OAuth2TokenType ID_TOKEN_TOKEN_TYPE = new OAuth2TokenType(
            OidcParameterNames.ID_TOKEN);
    private final Log logger = LogFactory
            .getLog(getClass());
    private final OAuth2AuthorizationService authorizationService;
    private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;

    // Constructs an OAuth2RefreshTokenAuthenticationProvider using the provided parameters.
    // Parameter authorizationService the authorization service
    // Parameter tokenGenerator the token generator
    // Since 0.2.3
    public OAuth2RefreshTokenAuthenticationProvider(OAuth2AuthorizationService authorizationService,
                                                    OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator) {
        Assert.notNull(authorizationService, "authorizationService 不能为空");
        Assert.notNull(tokenGenerator, "tokenGenerator 不能为空");
        this.authorizationService = authorizationService;
        this.tokenGenerator = tokenGenerator;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OAuth2RefreshTokenAuthenticationToken refreshTokenAuthentication = (OAuth2RefreshTokenAuthenticationToken) authentication;

        OAuth2ClientAuthenticationToken clientPrincipal = getAuthenticatedClientElseThrowInvalidClient(
                refreshTokenAuthentication);
        RegisteredClient registeredClient = clientPrincipal.getRegisteredClient();

        if (this.logger.isTraceEnabled()) {
            this.logger.trace("已获取注册客户端");
        }

        OAuth2Authorization authorization = this.authorizationService.findByToken(
                refreshTokenAuthentication.getRefreshToken(), OAuth2TokenType.REFRESH_TOKEN);
        if (authorization == null) {
            if (this.logger.isTraceEnabled()) {
                this.logger.trace("刷新令牌无效。");
            }
            OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT,
                    "刷新令牌无效或已过期。", ERROR_URI);
            throw new OAuth2AuthenticationException(error);
        }

        if (this.logger.isTraceEnabled()) {
            this.logger.trace("已通过刷新令牌获取授权");
        }

        if (!registeredClient.getId().equals(authorization.getRegisteredClientId())) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }

        if (!registeredClient.getAuthorizationGrantTypes()
                .contains(AuthorizationGrantType.REFRESH_TOKEN)) {
            if (this.logger.isTraceEnabled()) {
                this.logger.warn(LogMessage.format(
                        "无效请求：注册客户端 '%s' 不允许使用请求的 grant_type",
                        registeredClient.getId()));
            }
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
        }

        OAuth2Authorization.Token<OAuth2RefreshToken> refreshToken = authorization
                .getRefreshToken();
        if (!refreshToken.isActive()) {
            // As per https://tools.ietf.org/html/rfc6749#section-5.2
            // invalid_grant: The provided authorization grant (e.g., authorization code,
            // resource owner credentials) or refresh token is invalid, expired, revoked [...].
            if (this.logger.isTraceEnabled()) {
                this.logger.trace("刷新令牌已过期。");
            }
            OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT,
                    "刷新令牌无效或已过期。", ERROR_URI);
            throw new OAuth2AuthenticationException(error);
        }

        // As per https://tools.ietf.org/html/rfc6749#section-6
        // The requested scope MUST NOT include any scope not originally granted by the resource owner,
        // and if omitted is treated as equal to the scope originally granted by the resource owner.
        Set<String> scopes = refreshTokenAuthentication.getScopes();
        Set<String> authorizedScopes = authorization.getAuthorizedScopes();
        if (!authorizedScopes.containsAll(scopes)) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_SCOPE);
        }

        if (this.logger.isTraceEnabled()) {
            this.logger.trace("已校验令牌请求参数");
        }

        if (scopes.isEmpty()) {
            scopes = authorizedScopes;
        }

		DefaultOAuth2TokenContext.Builder tokenContextBuilder = DefaultOAuth2TokenContext.builder()
				.registeredClient(registeredClient)
				.principal(authorization.getAttribute(Principal.class.getName()))
				.authorizationServerContext(AuthorizationServerContextHolder.getContext())
				.authorization(authorization)
				.authorizedScopes(scopes)
				.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
				.authorizationGrant(refreshTokenAuthentication);

        OAuth2Authorization.Builder authorizationBuilder = OAuth2Authorization.from(authorization);

        // ----- Access token -----
        OAuth2TokenContext tokenContext = tokenContextBuilder
                .tokenType(OAuth2TokenType.ACCESS_TOKEN).build();
        OAuth2Token generatedAccessToken = this.tokenGenerator.generate(tokenContext);
        if (generatedAccessToken == null) {
            OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR,
                    "令牌生成器生成访问令牌失败。", ERROR_URI);
            throw new OAuth2AuthenticationException(error);
        }

        if (this.logger.isTraceEnabled()) {
            this.logger.trace("已生成访问令牌");
        }

        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                generatedAccessToken.getTokenValue(), generatedAccessToken.getIssuedAt(),
                generatedAccessToken.getExpiresAt(), tokenContext.getAuthorizedScopes());
        if (generatedAccessToken instanceof ClaimAccessor) {
            authorizationBuilder.token(accessToken, (metadata) -> {
                metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME,
                        ((ClaimAccessor) generatedAccessToken).getClaims());
                metadata.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, false);
            });
        } else {
            authorizationBuilder.accessToken(accessToken);
        }

        // ----- Refresh token -----
        OAuth2RefreshToken currentRefreshToken = refreshToken.getToken();
        if (!registeredClient.getTokenSettings().isReuseRefreshTokens()) {
            tokenContext = tokenContextBuilder.tokenType(OAuth2TokenType.REFRESH_TOKEN).build();
            OAuth2Token generatedRefreshToken = this.tokenGenerator.generate(tokenContext);
            if (!(generatedRefreshToken instanceof OAuth2RefreshToken)) {
                OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR,
                        "令牌生成器生成刷新令牌失败。", ERROR_URI);
                throw new OAuth2AuthenticationException(error);
            }

            if (this.logger.isTraceEnabled()) {
                this.logger.trace("已生成刷新令牌");
            }

            currentRefreshToken = (OAuth2RefreshToken) generatedRefreshToken;
            authorizationBuilder.refreshToken(currentRefreshToken);
        }

        // ----- ID token -----
        OidcIdToken idToken;
        if (authorizedScopes.contains(OidcScopes.OPENID)) {
			tokenContext = tokenContextBuilder
					.tokenType(ID_TOKEN_TOKEN_TYPE)
// ID token customizer may need access to the access token and/or refresh token
					.authorization(authorizationBuilder.build())
					.build();
            OAuth2Token generatedIdToken = this.tokenGenerator.generate(tokenContext);
            if (!(generatedIdToken instanceof Jwt)) {
                OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR,
                        "令牌生成器生成 ID 令牌失败。", ERROR_URI);
                throw new OAuth2AuthenticationException(error);
            }

            if (this.logger.isTraceEnabled()) {
                this.logger.trace("已生成 ID 令牌");
            }

            idToken = new OidcIdToken(generatedIdToken.getTokenValue(),
                    generatedIdToken.getIssuedAt(), generatedIdToken.getExpiresAt(),
                    ((Jwt) generatedIdToken).getClaims());
            authorizationBuilder.token(idToken, (metadata) -> metadata
                    .put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, idToken.getClaims()));
        } else {
            idToken = null;
        }

        authorization = authorizationBuilder.build();

        this.authorizationService.save(authorization);

        if (this.logger.isTraceEnabled()) {
            this.logger.trace("已保存授权");
        }

        Map<String, Object> additionalParameters = Collections.emptyMap();
        if (idToken != null) {
            additionalParameters = new HashMap<>();
            additionalParameters.put(OidcParameterNames.ID_TOKEN, idToken.getTokenValue());
        }

        if (this.logger.isTraceEnabled()) {
            this.logger.trace("令牌请求认证成功");
        }

        return new OAuth2AccessTokenAuthenticationToken(registeredClient, clientPrincipal,
                accessToken, currentRefreshToken, additionalParameters);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2RefreshTokenAuthenticationToken.class.isAssignableFrom(authentication);
    }

}
