package cn.tolim.pudans.iam.protocol.oidc.authentication;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.log.LogMessage;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationValidator;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Set;
import java.util.function.Consumer;

import static cn.tolim.pudans.iam.protocol.oidc.authentication.OAuth2AuthorizationImplicitRequestAuthenticationProvider.IMPLICIT;
import static cn.tolim.pudans.iam.protocol.oidc.endpoint.OAuth2ParameterNames.*;
import static org.springframework.security.oauth2.core.OAuth2ErrorCodes.INVALID_REQUEST;


@SuppressWarnings("AlibabaClassNamingShouldBeCamel")
public final class OAuth2AuthorizationImplicitRequestAuthenticationValidator implements
        Consumer<OAuth2AuthorizationImplicitRequestAuthenticationContext> {
    // The default validator for OAuth2AuthorizationImplicitRequestAuthenticationToken#getScopes().
    public static final Consumer<OAuth2AuthorizationImplicitRequestAuthenticationContext> DEFAULT_SCOPE_VALIDATOR = OAuth2AuthorizationImplicitRequestAuthenticationValidator::validateScope;
    // The default validator for OAuth2AuthorizationImplicitRequestAuthenticationToken#getRedirectUri().
    public static final Consumer<OAuth2AuthorizationImplicitRequestAuthenticationContext> DEFAULT_REDIRECT_URI_VALIDATOR = OAuth2AuthorizationImplicitRequestAuthenticationValidator::validateRedirectUri;
    public static final Consumer<OAuth2AuthorizationImplicitRequestAuthenticationContext> DEFAULT_GRANT_TYPE_VALIDATOR = OAuth2AuthorizationImplicitRequestAuthenticationValidator::validateGrantType;
    public static final Consumer<OAuth2AuthorizationImplicitRequestAuthenticationContext> DEFAULT_VALIDATE_RESPONSE_MODE = OAuth2AuthorizationImplicitRequestAuthenticationValidator::validateResponseMode;
    private static final String ERROR_URI = "https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.2.1";
    private static final Log LOGGER = LogFactory
            .getLog(OAuth2AuthorizationCodeRequestAuthenticationValidator.class);
    private final Consumer<OAuth2AuthorizationImplicitRequestAuthenticationContext> authenticationValidator = DEFAULT_REDIRECT_URI_VALIDATOR
            .andThen(DEFAULT_SCOPE_VALIDATOR).andThen(DEFAULT_GRANT_TYPE_VALIDATOR)
            .andThen(DEFAULT_VALIDATE_RESPONSE_MODE);

    // Validate scope
    // Parameter authenticationContext OAuth2AuthorizationImplicitRequestAuthenticationContext
    private static void validateScope(OAuth2AuthorizationImplicitRequestAuthenticationContext authenticationContext) {
        RegisteredClient registeredClient = authenticationContext.getRegisteredClient();
        OAuth2AuthorizationImplicitRequestAuthenticationToken authorizationImplicitRequestAuthenticationToken = authenticationContext
                .getAuthentication();
        Set<String> requestedScopes = authorizationImplicitRequestAuthenticationToken.getScopes();
        Set<String> allowedScopes = registeredClient.getScopes();

        if (!requestedScopes.isEmpty() && !allowedScopes.containsAll(requestedScopes)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        LogMessage.format("无效请求：请求的 scope 不被允许"
                                        + "，注册客户端为 '%s'",
                                registeredClient.getId()));
            }
            throwError(OAuth2ErrorCodes.INVALID_SCOPE, OAuth2ParameterNames.SCOPE,
                    authorizationImplicitRequestAuthenticationToken, registeredClient);
        }

    }

    // Validate grant type
    // Parameter authenticationContext OAuth2AuthorizationImplicitRequestAuthenticationContext
    private static void validateGrantType(OAuth2AuthorizationImplicitRequestAuthenticationContext authenticationContext) {
        OAuth2AuthorizationImplicitRequestAuthenticationToken authorizationCodeRequestAuthentication = authenticationContext
                .getAuthentication();
        RegisteredClient registeredClient = authenticationContext.getRegisteredClient();
        // Validate grant type
        if (!registeredClient.getAuthorizationGrantTypes().contains(IMPLICIT)) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.warn(LogMessage.format(
                        "无效请求：注册客户端 '%s' 不允许使用请求的 grant_type",
                        registeredClient.getId()));
            }
            throwError(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT, OAuth2ParameterNames.CLIENT_ID,
                    authorizationCodeRequestAuthentication, registeredClient);
        }
    }

    // Validate redirect URI
    // Parameter authenticationContext OAuth2AuthorizationImplicitRequestAuthenticationContext
    private static void validateRedirectUri(OAuth2AuthorizationImplicitRequestAuthenticationContext authenticationContext) {
        OAuth2AuthorizationImplicitRequestAuthenticationToken authorizationImplicitRequestAuthentication = authenticationContext
                .getAuthentication();
        RegisteredClient registeredClient = authenticationContext.getRegisteredClient();

        String requestedRedirectUri = authorizationImplicitRequestAuthentication.getRedirectUri();

        if (StringUtils.hasText(requestedRedirectUri)) {
            // ***** redirect_uri is available in authorization request

            UriComponents requestedRedirect = null;
            try {
                requestedRedirect = UriComponentsBuilder.fromUriString(requestedRedirectUri)
                        .build();
            } catch (Exception ex) {
            }
            if (requestedRedirect == null || requestedRedirect.getFragment() != null) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(LogMessage
                            .format("无效请求：redirect_uri 缺失或包含片段"
                                            + "，注册客户端为 '%s'",
                                    registeredClient.getId()));
                }
                throwError(OAuth2ErrorCodes.INVALID_REQUEST, OAuth2ParameterNames.REDIRECT_URI,
                        authorizationImplicitRequestAuthentication, registeredClient);
            }

            if (!isLoopbackAddress(requestedRedirect.getHost())) {
                // As per https://datatracker.ietf.org/doc/html/draft-ietf-oauth-security-topics-22#section-4.1.3
                // When comparing client redirect URIs against pre-registered URIs,
                // authorization servers MUST utilize exact string matching.
                if (!registeredClient.getRedirectUris().contains(requestedRedirectUri)) {
                    throwError(OAuth2ErrorCodes.INVALID_REQUEST, OAuth2ParameterNames.REDIRECT_URI,
                            authorizationImplicitRequestAuthentication, registeredClient);
                }
            } else {
                // As per https://datatracker.ietf.org/doc/html/draft-ietf-oauth-v2-1-08#section-8.4.2
                // The authorization server MUST allow any port to be specified at the
                // time of the request for loopback IP redirect URIs, to accommodate
                // clients that obtain an available ephemeral port from the operating
                // system at the time of the request.
                boolean validRedirectUri = false;
                for (String registeredRedirectUri : registeredClient.getRedirectUris()) {
                    UriComponentsBuilder registeredRedirect = UriComponentsBuilder
                            .fromUriString(registeredRedirectUri);
                    registeredRedirect.port(requestedRedirect.getPort());
                    if (registeredRedirect.build().toString()
                            .equals(requestedRedirect.toString())) {
                        validRedirectUri = true;
                        break;
                    }
                }
                if (!validRedirectUri) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug(LogMessage
                                .format("无效请求：redirect_uri 不匹配"
                                                + "，注册客户端为 '%s'",
                                        registeredClient.getId()));
                    }
                    throwError(OAuth2ErrorCodes.INVALID_REQUEST, OAuth2ParameterNames.REDIRECT_URI,
                            authorizationImplicitRequestAuthentication, registeredClient);
                }
            }

        } else {
            // ***** redirect_uri is NOT available in authorization request

            if (authorizationImplicitRequestAuthentication.getScopes().contains(OidcScopes.OPENID)
                    || registeredClient.getRedirectUris().size() != 1) {
                // redirect_uri is REQUIRED for OpenID Connect
                throwError(OAuth2ErrorCodes.INVALID_REQUEST, OAuth2ParameterNames.REDIRECT_URI,
                        authorizationImplicitRequestAuthentication, registeredClient);
            }
        }
    }

    private static boolean isLoopbackAddress(String host) {
        if (!StringUtils.hasText(host)) {
            return false;
        }
        // IPv6 loopback address should either be "0:0:0:0:0:0:0:1" or "::1"
        if ("[0:0:0:0:0:0:0:1]".equals(host) || "[::1]".equals(host)) {
            return true;
        }
        // IPv4 loopback address ranges from 127.0.0.1 to 127.255.255.255
        String[] ipv4Octets = host.split("\\.");
        if (ipv4Octets.length != 4) {
            return false;
        }
        try {
            int[] address = new int[ipv4Octets.length];
            for (int i = 0; i < ipv4Octets.length; i++) {
                address[i] = Integer.parseInt(ipv4Octets[i]);
            }
            return address[0] == 127 && address[1] >= 0 && address[1] <= 255 && address[2] >= 0
                    && address[2] <= 255 && address[3] >= 1 && address[3] <= 255;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    // Validate response mode
    // Parameter authenticationContext OAuth2AuthorizationImplicitRequestAuthenticationContext
    private static void validateResponseMode(OAuth2AuthorizationImplicitRequestAuthenticationContext authenticationContext) {
        OAuth2AuthorizationImplicitRequestAuthenticationToken implicitRequestAuthenticationToken = authenticationContext
                .getAuthentication();
        RegisteredClient registeredClient = authenticationContext.getRegisteredClient();
        if (implicitRequestAuthenticationToken.getResponseMode() == null) {
            return;
        }
        if (!FRAGMENT.equals(implicitRequestAuthenticationToken.getResponseMode())
                || !QUERY.equals(implicitRequestAuthenticationToken.getResponseMode())) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.warn(
                        LogMessage.format("注册客户端 '%s' 使用了已失效的 response_type",
                                registeredClient.getId()));
            }
            throwError(INVALID_REQUEST, RESPONSE_MODE, implicitRequestAuthenticationToken,
                    registeredClient);
        }
    }

    private static void throwError(String errorCode, String parameterName,
                                   OAuth2AuthorizationImplicitRequestAuthenticationToken authorizationCodeRequestAuthentication,
                                   RegisteredClient registeredClient) {
        OAuth2Error error = new OAuth2Error(errorCode, "OAuth 2.0 参数：" + parameterName,
                ERROR_URI);
        throwError(error, parameterName, authorizationCodeRequestAuthentication, registeredClient);
    }

    private static void throwError(OAuth2Error error, String parameterName,
                                   OAuth2AuthorizationImplicitRequestAuthenticationToken authorizationCodeRequestAuthentication,
                                   RegisteredClient registeredClient) {

        String redirectUri = StringUtils
                .hasText(authorizationCodeRequestAuthentication.getRedirectUri())
                ? authorizationCodeRequestAuthentication.getRedirectUri()
                : registeredClient.getRedirectUris().iterator().next();
        if (error.getErrorCode().equals(INVALID_REQUEST)
                && parameterName.equals(OAuth2ParameterNames.REDIRECT_URI)) {
            // Prevent redirects
            redirectUri = null;
        }

        throw new OAuth2AuthorizationImplicitRequestAuthenticationException(error,
                getRequestAuthenticationToken(authorizationCodeRequestAuthentication, redirectUri));
    }

    @NotNull
    private static OAuth2AuthorizationImplicitRequestAuthenticationToken getRequestAuthenticationToken(OAuth2AuthorizationImplicitRequestAuthenticationToken requestAuthenticationToken,
                                                                                                       String redirectUri) {
        OAuth2AuthorizationImplicitRequestAuthenticationToken authorizationImplicitRequestAuthenticationToken = new OAuth2AuthorizationImplicitRequestAuthenticationToken(
                requestAuthenticationToken.getAuthorizationUri(),
                requestAuthenticationToken.getClientId(),
                (Authentication) requestAuthenticationToken.getPrincipal(), redirectUri,
                requestAuthenticationToken.getState(), requestAuthenticationToken.getScopes(),
                requestAuthenticationToken.getResponseTypes(),
                requestAuthenticationToken.getResponseMode(),
                requestAuthenticationToken.getAdditionalParameters());
        authorizationImplicitRequestAuthenticationToken.setAuthenticated(true);
        return authorizationImplicitRequestAuthenticationToken;
    }

    @Override
    public void accept(OAuth2AuthorizationImplicitRequestAuthenticationContext authenticationContext) {
        this.authenticationValidator.accept(authenticationContext);
    }

}
