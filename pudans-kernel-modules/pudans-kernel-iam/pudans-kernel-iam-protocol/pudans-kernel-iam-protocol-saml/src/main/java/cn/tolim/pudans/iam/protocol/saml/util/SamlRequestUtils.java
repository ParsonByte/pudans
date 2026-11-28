package cn.tolim.pudans.iam.protocol.saml.util;

import cn.tolim.pudans.iam.core.context.ContextService;
import cn.tolim.pudans.iam.protocol.saml.constant.SamlProtocolConstants.SamlEndpointConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.AntPathMatcher;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static cn.tolim.pudans.iam.communal.constant.ProtocolConstants.APP_CODE;


public final class SamlRequestUtils {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private SamlRequestUtils() {
    }

    // Resolve the request path used by the Ant path matcher.
    public static String getRequestPath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        String pathInfo = request.getPathInfo();
        if (servletPath == null) {
            return request.getRequestURI();
        }
        return pathInfo == null ? servletPath : servletPath + pathInfo;
    }

    // Resolve the application code from the SAML endpoint path.
    public static String resolveAppCode(HttpServletRequest request) {
        String path = getRequestPath(request);
        if (!PATH_MATCHER.match(SamlEndpointConstants.SAML_AUTHORIZE_BASE_PATH, path)) {
            return null;
        }
        Map<String, String> variables = PATH_MATCHER.extractUriTemplateVariables(
                SamlEndpointConstants.SAML_AUTHORIZE_BASE_PATH, path);
        return variables.get(APP_CODE);
    }

    // Build the login page redirect that returns to the given URL.
    public static String buildLoginRedirect(String returnUrl) {
        String encoded = URLEncoder.encode(returnUrl, StandardCharsets.UTF_8);
        return ContextService.getPortalLoginUrl() + "?redirect=" + encoded;
    }

    // Build the full URL of the current request including the query string.
    public static String currentRequestUrl(HttpServletRequest request) {
        StringBuilder builder = new StringBuilder(request.getRequestURL());
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            builder.append('?').append(request.getQueryString());
        }
        return builder.toString();
    }
}