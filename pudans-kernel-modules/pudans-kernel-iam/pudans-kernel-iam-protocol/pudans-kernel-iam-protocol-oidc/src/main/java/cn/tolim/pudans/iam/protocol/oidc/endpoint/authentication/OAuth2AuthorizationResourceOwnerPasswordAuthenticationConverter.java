package cn.tolim.pudans.iam.protocol.oidc.endpoint.authentication;

import cn.tolim.pudans.iam.protocol.oidc.authentication.OAuth2AuthorizationResourceOwnerPasswordAuthenticationToken;
import cn.tolim.pudans.iam.protocol.oidc.endpoint.OAuth2ParameterNames;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.util.*;

import static cn.tolim.pudans.iam.infra.util.HttpRequestUtils.getFormParameters;
import static cn.tolim.pudans.iam.infra.util.HttpRequestUtils.getQueryParameters;
import static cn.tolim.pudans.iam.protocol.oidc.endpoint.OAuth2EndpointUtils.throwError;
import static org.springframework.http.HttpMethod.GET;


@SuppressWarnings("AlibabaClassNamingShouldBeCamel")
public final class OAuth2AuthorizationResourceOwnerPasswordAuthenticationConverter implements
        AuthenticationConverter {
    static final String ACCESS_TOKEN_REQUEST_ERROR_URI = "https://datatracker.ietf.org/doc/html/rfc6749#section-5.2";

    @Nullable
    @Override
    public Authentication convert(HttpServletRequest request) {
        // grant_type (required)
        String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);
        if (!OAuth2AuthorizationResourceOwnerPasswordAuthenticationToken.PASSWORD.getValue()
                .equals(grantType)) {
            return null;
        }
        Authentication clientPrincipal = SecurityContextHolder.getContext().getAuthentication();
        // Get parameters
        MultiValueMap<String, String> parameters = GET.name().equals(request.getMethod())
                ? getQueryParameters(request)
                : getFormParameters(request);
        // username (required)
        String username = parameters.getFirst(OAuth2ParameterNames.USERNAME);
        if (!StringUtils.hasText(username)
                || parameters.get(OAuth2ParameterNames.USERNAME).size() != 1) {
            throwError(OAuth2ErrorCodes.INVALID_REQUEST, OAuth2ParameterNames.USERNAME,
                    ACCESS_TOKEN_REQUEST_ERROR_URI);
        }
        // password (required)
        String password = parameters.getFirst(OAuth2ParameterNames.PASSWORD);
        if (!StringUtils.hasText(password)
                || parameters.get(OAuth2ParameterNames.PASSWORD).size() != 1) {
            throwError(OAuth2ErrorCodes.INVALID_REQUEST, OAuth2ParameterNames.PASSWORD,
                    ACCESS_TOKEN_REQUEST_ERROR_URI);
        }
        // scope (OPTIONAL)
        String scope = parameters.getFirst(OAuth2ParameterNames.SCOPE);
        if (StringUtils.hasText(scope) && parameters.get(OAuth2ParameterNames.SCOPE).size() != 1) {
            throwError(OAuth2ErrorCodes.INVALID_REQUEST, OAuth2ParameterNames.SCOPE,
                    ACCESS_TOKEN_REQUEST_ERROR_URI);
        }
        Set<String> requestedScopes = null;
        if (StringUtils.hasText(scope)) {
            requestedScopes = new HashSet<>(
                    Arrays.asList(StringUtils.delimitedListToStringArray(scope, " ")));
        }
        // Additional parameters
        Map<String, Object> additionalParameters = new HashMap<>(16);
        parameters.forEach((key, value) -> {
            if (!key.equals(OAuth2ParameterNames.GRANT_TYPE)
                    && !key.equals(OAuth2ParameterNames.CLIENT_ID)
                    && !key.equals(OAuth2ParameterNames.USERNAME)
                    && !key.equals(OAuth2ParameterNames.PASSWORD)) {
                additionalParameters.put(key,
                        (value.size() == 1) ? value.get(0) : value.toArray(new String[0]));
            }
        });
        return new OAuth2AuthorizationResourceOwnerPasswordAuthenticationToken(username, password,
                requestedScopes, clientPrincipal, additionalParameters);
    }

}
