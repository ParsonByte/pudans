package cn.tolim.pudans.iam.protocol.oidc.authentication;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.log.LogMessage;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.util.Set;
import java.util.function.Consumer;

import static cn.tolim.pudans.iam.protocol.oidc.authentication.OAuth2AuthorizationResourceOwnerPasswordAuthenticationToken.PASSWORD;


@SuppressWarnings({"AlibabaClassNamingShouldBeCamel", "AlibabaUndefineMagicConstant"})
public final class OAuth2AuthorizationResourceOwnerPasswordAuthenticationValidator implements
        Consumer<OAuth2AuthorizationResourceOwnerPasswordAuthenticationContext> {

    // The default validator for OAuth2AuthorizationResourceOwnerPasswordAuthenticationToken#getScopes().
    public static final Consumer<OAuth2AuthorizationResourceOwnerPasswordAuthenticationContext> DEFAULT_SCOPE_VALIDATOR = OAuth2AuthorizationResourceOwnerPasswordAuthenticationValidator::validateScope;
    public static final Consumer<OAuth2AuthorizationResourceOwnerPasswordAuthenticationContext> DEFAULT_GRANT_TYPE_VALIDATOR = OAuth2AuthorizationResourceOwnerPasswordAuthenticationValidator::validateGrantType;
    private static final Log LOGGER = LogFactory
            .getLog(OAuth2AuthorizationResourceOwnerPasswordAuthenticationValidator.class);
    private static final String ERROR_URI = "https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.2.1";
    private final Consumer<OAuth2AuthorizationResourceOwnerPasswordAuthenticationContext> authenticationValidator = DEFAULT_SCOPE_VALIDATOR
            .andThen(DEFAULT_GRANT_TYPE_VALIDATOR);

    // Validate scope
    // Parameter authenticationContext OAuth2AuthorizationResourceOwnerPasswordAuthenticationContext
    private static void validateScope(OAuth2AuthorizationResourceOwnerPasswordAuthenticationContext authenticationContext) {
        RegisteredClient registeredClient = authenticationContext.getRegisteredClient();
        OAuth2AuthorizationResourceOwnerPasswordAuthenticationToken authorizationPasswordAuthenticationToken = authenticationContext
                .getAuthentication();
        Set<String> requestedScopes = authorizationPasswordAuthenticationToken.getScopes();
        Set<String> allowedScopes = registeredClient.getScopes();
        if (!requestedScopes.isEmpty() && !allowedScopes.containsAll(requestedScopes)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        LogMessage.format("无效请求：请求的 scope 不被允许"
                                        + "，注册客户端为 '%s'",
                                registeredClient.getId()));
            }
            throwError(OAuth2ErrorCodes.INVALID_SCOPE, OAuth2ParameterNames.SCOPE,
                    authorizationPasswordAuthenticationToken);
        }
    }

    // Validate grant type
    // Parameter authenticationContext OAuth2AuthorizationResourceOwnerPasswordAuthenticationContext
    private static void validateGrantType(OAuth2AuthorizationResourceOwnerPasswordAuthenticationContext authenticationContext) {
        OAuth2AuthorizationResourceOwnerPasswordAuthenticationToken authorizationCodeRequestAuthentication = authenticationContext
                .getAuthentication();
        RegisteredClient registeredClient = authenticationContext.getRegisteredClient();
        // Validate grant type
        if (!registeredClient.getAuthorizationGrantTypes().contains(PASSWORD)) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.warn(LogMessage.format(
                        "无效请求：注册客户端 '%s' 不允许使用请求的 grant_type",
                        registeredClient.getId()));
            }
            throwError(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT, OAuth2ParameterNames.CLIENT_ID,
                    authorizationCodeRequestAuthentication);
        }
    }

    private static void throwError(String errorCode, String parameterName,
                                   OAuth2AuthorizationResourceOwnerPasswordAuthenticationToken authorizationCodeRequestAuthentication) {
        OAuth2Error error = new OAuth2Error(errorCode, "OAuth 2.0 参数：" + parameterName,
                ERROR_URI);
        throwError(error, authorizationCodeRequestAuthentication);
    }

    private static void throwError(OAuth2Error error,
                                   OAuth2AuthorizationResourceOwnerPasswordAuthenticationToken authorizationPasswordAuthenticationToken) {
        authorizationPasswordAuthenticationToken.setAuthenticated(true);
        throw new OAuth2AuthorizationResourceOwnerPasswordAuthenticationException(error,
                authorizationPasswordAuthenticationToken);
    }

    @Override
    public void accept(OAuth2AuthorizationResourceOwnerPasswordAuthenticationContext authenticationContext) {
        this.authenticationValidator.accept(authenticationContext);
    }

}
