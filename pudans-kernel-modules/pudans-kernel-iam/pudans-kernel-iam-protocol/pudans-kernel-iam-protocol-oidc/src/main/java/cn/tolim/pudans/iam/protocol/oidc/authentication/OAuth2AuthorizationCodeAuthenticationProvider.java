package cn.tolim.pudans.iam.protocol.oidc.authentication;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.log.LogMessage;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.*;

import static cn.tolim.pudans.iam.protocol.oidc.authentication.OAuth2AuthenticationProviderUtils.getAuthenticatedClientElseThrowInvalidClient;


public final class OAuth2AuthorizationCodeAuthenticationProvider implements AuthenticationProvider {
    private static final String ERROR_URI = "https://datatracker.ietf.org/doc/html/rfc6749#section-5.2";
    private static final OAuth2TokenType AUTHORIZATION_CODE_TOKEN_TYPE = new OAuth2TokenType(
            OAuth2ParameterNames.CODE);
    private static final OAuth2TokenType ID_TOKEN_TOKEN_TYPE = new OAuth2TokenType(
            OidcParameterNames.ID_TOKEN);
    private final Log logger = LogFactory
            .getLog(getClass());
    private final OAuth2AuthorizationService authorizationService;
    private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;
    private SessionRegistry sessionRegistry;

    // Constructs an OAuth2AuthorizationCodeAuthenticationProvider using the provided parameters.
    // Parameter authorizationService the authorization service
    // Parameter tokenGenerator the token generator
    // Since 0.2.3
    public OAuth2AuthorizationCodeAuthenticationProvider(OAuth2AuthorizationService authorizationService,
                                                         OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator) {
        Assert.notNull(authorizationService, "authorizationService 不能为空");
        Assert.notNull(tokenGenerator, "tokenGenerator 不能为空");
        this.authorizationService = authorizationService;
        this.tokenGenerator = tokenGenerator;
    }

    private static String createHash(String value) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(value.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OAuth2AuthorizationCodeAuthenticationToken authorizationCodeAuthentication = (OAuth2AuthorizationCodeAuthenticationToken) authentication;

        OAuth2ClientAuthenticationToken clientPrincipal = getAuthenticatedClientElseThrowInvalidClient(
                authorizationCodeAuthentication);
        RegisteredClient registeredClient = clientPrincipal.getRegisteredClient();

        if (this.logger.isTraceEnabled()) {
            this.logger.trace("已获取注册客户端");
        }

        OAuth2Authorization authorization = this.authorizationService
                .findByToken(authorizationCodeAuthentication.getCode(), AUTHORIZATION_CODE_TOKEN_TYPE);
        if (authorization == null) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }

        if (this.logger.isTraceEnabled()) {
            this.logger.trace("已通过授权码获取授权");
        }

        OAuth2Authorization.Token<OAuth2AuthorizationCode> authorizationCode = authorization
                .getToken(OAuth2AuthorizationCode.class);

        OAuth2AuthorizationRequest authorizationRequest = authorization
                .getAttribute(OAuth2AuthorizationRequest.class.getName());

        if (!registeredClient.getClientId().equals(authorizationRequest.getClientId())) {
            if (!authorizationCode.isInvalidated()) {
                // Invalidate the authorization code given that a different client is attempting to use it
                authorization = OAuth2AuthenticationProviderUtils.invalidate(authorization,
                        authorizationCode.getToken());
                this.authorizationService.save(authorization);
                if (this.logger.isWarnEnabled()) {
                    this.logger.warn(LogMessage.format(
                            "注册客户端 '%s' 使用了已失效的授权码",
                            registeredClient.getId()));
                }
            }
            OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT,
                    "授权码无效或已过期。",
                    ERROR_URI);
            throw new OAuth2AuthenticationException(error);
        }

        if (StringUtils.hasText(authorizationRequest.getRedirectUri()) && !authorizationRequest
                .getRedirectUri().equals(authorizationCodeAuthentication.getRedirectUri())) {
            if (this.logger.isWarnEnabled()) {
                this.logger.warn(
                        LogMessage.format("注册客户端 '%s' 使用了已失效的 redirect_uri",
                                registeredClient.getId()));
            }
            OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT,
                    "redirect_uri 与授权请求中使用的重定向地址不匹配。",
                    ERROR_URI);
            throw new OAuth2AuthenticationException(error);
        }

        if (!authorizationCode.isActive()) {
            if (authorizationCode.isInvalidated()) {
                OAuth2Authorization.Token<? extends OAuth2Token> token = authorization
                        .getRefreshToken() != null ? authorization.getRefreshToken()
                        : authorization.getAccessToken();
                if (token != null) {
                    // Invalidate the access (and refresh) token as the client is attempting to use the authorization code more than once
                    authorization = OAuth2AuthenticationProviderUtils.invalidate(authorization,
                            token.getToken());
                    this.authorizationService.save(authorization);
                    if (this.logger.isWarnEnabled()) {
                        this.logger.warn(LogMessage.format(
                                "注册客户端 '%s' 此前签发的授权令牌已失效",
                                registeredClient.getId()));
                    }
                }
            }
            OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT,
                    "授权码无效或已过期。", ERROR_URI);
            throw new OAuth2AuthenticationException(error);
        }

        if (this.logger.isTraceEnabled()) {
            this.logger.trace("已校验令牌请求参数");
        }

        Authentication principal = authorization.getAttribute(Principal.class.getName());

		DefaultOAuth2TokenContext.Builder tokenContextBuilder = DefaultOAuth2TokenContext.builder()
				.registeredClient(registeredClient)
				.principal(principal)
				.authorizationServerContext(AuthorizationServerContextHolder.getContext())
				.authorization(authorization)
				.authorizedScopes(authorization.getAuthorizedScopes())
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.authorizationGrant(authorizationCodeAuthentication);

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
            authorizationBuilder.token(accessToken,
                    (metadata) -> metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME,
                            ((ClaimAccessor) generatedAccessToken).getClaims()));
        } else {
            authorizationBuilder.accessToken(accessToken);
        }

        // ----- Refresh token -----
        OAuth2RefreshToken refreshToken = null;
        if (registeredClient.getAuthorizationGrantTypes()
                .contains(AuthorizationGrantType.REFRESH_TOKEN)) {
            tokenContext = tokenContextBuilder.tokenType(OAuth2TokenType.REFRESH_TOKEN).build();
            OAuth2Token generatedRefreshToken = this.tokenGenerator.generate(tokenContext);
            if (generatedRefreshToken != null) {
                if (!(generatedRefreshToken instanceof OAuth2RefreshToken)) {
                    OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR,
                            "令牌生成器生成有效的刷新令牌失败。", ERROR_URI);
                    throw new OAuth2AuthenticationException(error);
                }

                if (this.logger.isTraceEnabled()) {
                    this.logger.trace("已生成刷新令牌");
                }

                refreshToken = (OAuth2RefreshToken) generatedRefreshToken;
                authorizationBuilder.refreshToken(refreshToken);
            }
        }

        // ----- ID token -----
        OidcIdToken idToken;
        if (authorizationRequest.getScopes().contains(OidcScopes.OPENID)) {
            SessionInformation sessionInformation = getSessionInformation(principal);
            if (sessionInformation != null) {
                try {
                    // Compute (and use) hash for Session ID
                    sessionInformation = new SessionInformation(sessionInformation.getPrincipal(),
                            createHash(sessionInformation.getSessionId()),
                            sessionInformation.getLastRequest());
                } catch (NoSuchAlgorithmException ex) {
                    OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR,
                            "计算会话 ID 哈希失败。", ERROR_URI);
                    throw new OAuth2AuthenticationException(error);
                }
                tokenContextBuilder.put(SessionInformation.class, sessionInformation);
            }
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

        // Invalidate the authorization code as it can only be used once
        authorization = OAuth2AuthenticationProviderUtils.invalidate(authorization,
                authorizationCode.getToken());

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
                accessToken, refreshToken, additionalParameters);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2AuthorizationCodeAuthenticationToken.class.isAssignableFrom(authentication);
    }

    // Sets the SessionRegistry used to track OpenID Connect sessions.
    // Parameter sessionRegistry the SessionRegistry used to track OpenID Connect sessions
    // Since 1.1
    public void setSessionRegistry(SessionRegistry sessionRegistry) {
        Assert.notNull(sessionRegistry, "sessionRegistry 不能为空");
        this.sessionRegistry = sessionRegistry;
    }

    private SessionInformation getSessionInformation(Authentication principal) {
        SessionInformation sessionInformation = null;
        if (this.sessionRegistry != null) {
            List<SessionInformation> sessions = this.sessionRegistry
                    .getAllSessions(principal.getPrincipal(), false);
            if (!CollectionUtils.isEmpty(sessions)) {
                sessionInformation = sessions.get(0);
                if (sessions.size() > 1) {
                    // Get the most recent session
                    sessions = new ArrayList<>(sessions);
                    sessions.sort(Comparator.comparing(SessionInformation::getLastRequest));
                    sessionInformation = sessions.get(sessions.size() - 1);
                }
            }
        }
        return sessionInformation;
    }

}
