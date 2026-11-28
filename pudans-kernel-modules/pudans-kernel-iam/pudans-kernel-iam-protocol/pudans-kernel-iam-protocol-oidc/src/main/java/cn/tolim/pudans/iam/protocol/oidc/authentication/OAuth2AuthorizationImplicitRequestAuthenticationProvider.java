package cn.tolim.pudans.iam.protocol.oidc.authentication;

import com.google.common.collect.Maps;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static cn.tolim.pudans.iam.infra.security.util.SecurityUtils.isPrincipalAuthenticated;
import static cn.tolim.pudans.iam.protocol.oidc.endpoint.OAuth2ParameterNames.RESPONSE_MODE;
import static cn.tolim.pudans.iam.protocol.oidc.endpoint.authentication.OAuth2AuthorizationImplicitRequestAuthenticationConverter.ID_TOKEN;
import static org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames.*;


@SuppressWarnings("AlibabaClassNamingShouldBeCamel")
public final class OAuth2AuthorizationImplicitRequestAuthenticationProvider implements
        AuthenticationProvider {
    public static final AuthorizationGrantType IMPLICIT = new AuthorizationGrantType(
            "implicit");
    private static final String ERROR_URI = "https://datatracker.ietf.org/doc/html/rfc6749#section-5.2";
    private static final OAuth2TokenType ID_TOKEN_TOKEN_TYPE = new OAuth2TokenType(
            OidcParameterNames.ID_TOKEN);
    private final Log logger = LogFactory
            .getLog(OAuth2AuthorizationImplicitRequestAuthenticationProvider.class);
    private final RegisteredClientRepository registeredClientRepository;
    private final OAuth2AuthorizationService authorizationService;
    private final OAuth2AuthorizationConsentService authorizationConsentService;
    private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;

    private Consumer<OAuth2AuthorizationImplicitRequestAuthenticationContext> authenticationValidator = new OAuth2AuthorizationImplicitRequestAuthenticationValidator();

    public OAuth2AuthorizationImplicitRequestAuthenticationProvider(RegisteredClientRepository registeredClientRepository,
                                                                    OAuth2AuthorizationService authorizationService,
                                                                    OAuth2AuthorizationConsentService authorizationConsentService,
                                                                    OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator) {
        Assert.notNull(registeredClientRepository, "registeredClientRepository 不能为空");
        Assert.notNull(authorizationService, "authorizationService 不能为空");
        Assert.notNull(authorizationConsentService, "authorizationConsentService 不能为空");
        Assert.notNull(tokenGenerator, "tokenGenerator 不能为空");
        this.tokenGenerator = tokenGenerator;
        this.registeredClientRepository = registeredClientRepository;
        this.authorizationService = authorizationService;
        this.authorizationConsentService = authorizationConsentService;
    }

    private static void throwError(OAuth2Error error,
                                   OAuth2AuthorizationImplicitRequestAuthenticationToken authorizationCodeRequestAuthentication) {

        String redirectUri = resolveRedirectUri(authorizationCodeRequestAuthentication);
        if (error.getErrorCode().equals(OAuth2ErrorCodes.INVALID_REQUEST)) {
            // Prevent redirects
            redirectUri = null;
        }
        OAuth2AuthorizationImplicitRequestAuthenticationToken authorizationImplicitRequestAuthenticationException = new OAuth2AuthorizationImplicitRequestAuthenticationToken(
                authorizationCodeRequestAuthentication.getAuthorizationUri(),
                authorizationCodeRequestAuthentication.getClientId(),
                (Authentication) authorizationCodeRequestAuthentication.getPrincipal(), redirectUri,
                authorizationCodeRequestAuthentication.getState(),
                authorizationCodeRequestAuthentication.getScopes(),
                authorizationCodeRequestAuthentication.getResponseTypes(),
                authorizationCodeRequestAuthentication.getResponseMode(),
                authorizationCodeRequestAuthentication.getAdditionalParameters());

        throw new OAuth2AuthorizationImplicitRequestAuthenticationException(error,
                authorizationImplicitRequestAuthenticationException);
    }

    private static String resolveRedirectUri(OAuth2AuthorizationImplicitRequestAuthenticationToken authorizationCodeRequestAuthentication) {
        if (authorizationCodeRequestAuthentication != null
                && StringUtils.hasText(authorizationCodeRequestAuthentication.getRedirectUri())) {
            return authorizationCodeRequestAuthentication.getRedirectUri();
        }
        return null;
    }

    public void setAuthenticationValidator(Consumer<OAuth2AuthorizationImplicitRequestAuthenticationContext> authenticationValidator) {
        Assert.notNull(authenticationValidator, "authenticationValidator 不能为空");
        this.authenticationValidator = authenticationValidator;
    }

    @Override
    @SuppressWarnings({"DuplicatedCode", "AlibabaMethodTooLong"})
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OAuth2AuthorizationImplicitRequestAuthenticationToken authorizationImplicitRequestAuthenticationToken = (OAuth2AuthorizationImplicitRequestAuthenticationToken) authentication;

        RegisteredClient registeredClient = this.registeredClientRepository.findByClientId(authorizationImplicitRequestAuthenticationToken.getClientId());

        if (registeredClient == null) {
            OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST, "OAuth 2.0 参数：" + OAuth2ParameterNames.CLIENT_ID, OAuth2AuthorizationImplicitRequestAuthenticationProvider.ERROR_URI);
            throwError(error, authorizationImplicitRequestAuthenticationToken);
            return null;
        }

        if (this.logger.isTraceEnabled()) {
            this.logger.trace("已获取注册客户端");
        }

        OAuth2AuthorizationImplicitRequestAuthenticationContext authenticationContext = OAuth2AuthorizationImplicitRequestAuthenticationContext.with(authorizationImplicitRequestAuthenticationToken).registeredClient(registeredClient).build();

        this.authenticationValidator.accept(authenticationContext);

        if (this.logger.isTraceEnabled()) {
            this.logger.trace("已校验令牌请求参数");
        }

        Authentication principal = (Authentication) authorizationImplicitRequestAuthenticationToken.getPrincipal();
        // Check authentication
        if (!isPrincipalAuthenticated(principal)) {
            if (this.logger.isTraceEnabled()) {
                this.logger.trace("主体未认证，未对授权码请求执行认证");
            }
            // Return the authorization request as-is where isAuthenticated() is false
            return authorizationImplicitRequestAuthenticationToken;
        }
        Set<String> authorizedScopes = authorizationImplicitRequestAuthenticationToken.getScopes();

        OAuth2ClientAuthenticationToken clientPrincipal = new OAuth2ClientAuthenticationToken(registeredClient, ClientAuthenticationMethod.NONE, null);

        DefaultOAuth2TokenContext.Builder tokenContextBuilder = DefaultOAuth2TokenContext.builder()
                .registeredClient(registeredClient)
                .principal(principal)
                .authorizationServerContext(AuthorizationServerContextHolder.getContext())
                .authorizedScopes(authorizedScopes)
                .authorizationGrantType(IMPLICIT)
                .authorizationGrant(authorizationImplicitRequestAuthenticationToken);

        OAuth2Authorization.Builder authorizationBuilder = OAuth2Authorization.withRegisteredClient(registeredClient)
                .principalName(principal.getName())
                .authorizationGrantType(IMPLICIT)
                .authorizedScopes(authorizedScopes)
                .attribute(Principal.class.getName(), principal);

        OAuth2TokenContext tokenContext;
        // ----- Access token -----
        OAuth2AccessToken accessToken;
        if (authorizationImplicitRequestAuthenticationToken.getResponseTypes().contains(OAuth2ParameterNames.TOKEN)) {
            tokenContext= tokenContextBuilder.tokenType(OAuth2TokenType.ACCESS_TOKEN).build();
            OAuth2Token generatedAccessToken = this.tokenGenerator.generate(tokenContext);
            if (generatedAccessToken == null) {
                OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR, "令牌生成器生成访问令牌失败。", ERROR_URI);
                throw new OAuth2AuthenticationException(error);
            }

            if (this.logger.isTraceEnabled()) {
                this.logger.trace("已生成访问令牌");
            }

            accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, generatedAccessToken.getTokenValue(), generatedAccessToken.getIssuedAt(), generatedAccessToken.getExpiresAt(), tokenContext.getAuthorizedScopes());
            if (generatedAccessToken instanceof ClaimAccessor) {
                authorizationBuilder.token(accessToken, (metadata) -> metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, ((ClaimAccessor) generatedAccessToken).getClaims()));
            } else {
                authorizationBuilder.accessToken(accessToken);
            }
        } else {
            accessToken = null;
        }

        // ----- ID token -----
        OidcIdToken idToken;
        if (authorizationImplicitRequestAuthenticationToken.getScopes().contains(OidcScopes.OPENID) && authorizationImplicitRequestAuthenticationToken.getResponseTypes().contains(ID_TOKEN.getValue())) {
            tokenContext = tokenContextBuilder
                    .tokenType(ID_TOKEN_TOKEN_TYPE)
                    // ID token customizer may need access to the access token and/or refresh token
                    .authorization(authorizationBuilder.build())
                    .build();
            OAuth2Token generatedIdToken = this.tokenGenerator.generate(tokenContext);
            if (!(generatedIdToken instanceof Jwt)) {
                OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR, "令牌生成器生成 ID 令牌失败。", ERROR_URI);
                throw new OAuth2AuthenticationException(error);
            }

            if (this.logger.isTraceEnabled()) {
                this.logger.trace("已生成 ID 令牌");
            }

            idToken = new OidcIdToken(generatedIdToken.getTokenValue(), generatedIdToken.getIssuedAt(), generatedIdToken.getExpiresAt(), ((Jwt) generatedIdToken).getClaims());
            authorizationBuilder.token(idToken, (metadata) -> metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, idToken.getClaims()));
        } else {
            idToken = null;
        }

        OAuth2Authorization authorization = authorizationBuilder.build();

        this.authorizationService.save(authorization);

        if (this.logger.isTraceEnabled()) {
            this.logger.trace("已保存授权");
        }

        Map<String, Object> additionalParameters = Maps.newHashMap();
        additionalParameters.put(REDIRECT_URI, authorizationImplicitRequestAuthenticationToken.getRedirectUri());
        additionalParameters.put(STATE , authorizationImplicitRequestAuthenticationToken.getState());
        additionalParameters.put(RESPONSE_MODE , authorizationImplicitRequestAuthenticationToken.getResponseMode());
        additionalParameters.put(RESPONSE_TYPE , org.apache.commons.lang3.StringUtils.join(authorizationImplicitRequestAuthenticationToken.getResponseTypes()," "));
        if (idToken != null) {
            additionalParameters.put(OidcParameterNames.ID_TOKEN, idToken.getTokenValue());
        }

        if (this.logger.isTraceEnabled()) {
            this.logger.trace("隐式请求认证成功");
        }

        OAuth2AuthorizationImplicitAccessTokenAuthenticationToken implicitAccessTokenAuthenticationToken = new OAuth2AuthorizationImplicitAccessTokenAuthenticationToken(registeredClient, clientPrincipal, accessToken, additionalParameters);
        implicitAccessTokenAuthenticationToken.setAuthenticated(true);
        return implicitAccessTokenAuthenticationToken;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2AuthorizationImplicitRequestAuthenticationToken.class
                .isAssignableFrom(authentication);
    }
}
