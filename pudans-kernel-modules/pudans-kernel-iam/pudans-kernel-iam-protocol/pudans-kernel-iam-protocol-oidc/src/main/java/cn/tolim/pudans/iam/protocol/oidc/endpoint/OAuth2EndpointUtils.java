package cn.tolim.pudans.iam.protocol.oidc.endpoint;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;


@SuppressWarnings("AlibabaClassNamingShouldBeCamel")
public class OAuth2EndpointUtils {
    private static final String DEFAULT_ERROR_URI = "https://datatracker.ietf.org/doc/html/rfc6749#section-5.2";

    // Throw exception
    // Parameter errorCode String Error code
    // Parameter parameterName String Parameter name
    public static void throwError(String errorCode, String parameterName) {
        throwError(errorCode, parameterName, DEFAULT_ERROR_URI);
    }

    // Throw exception
    // Parameter errorCode String Error code
    // Parameter parameterName String Parameter name
    // Parameter errorUri String Error URI
    public static void throwError(String errorCode, String parameterName, String errorUri) {
        OAuth2Error error = new OAuth2Error(errorCode, "OAuth 2.0 参数：" + parameterName,
                errorUri);
        throw new OAuth2AuthenticationException(error);
    }

    public static String appendUrl(String base, Map<String, ?> query, Map<String, String> keys,
                                   boolean fragment) {

        UriComponentsBuilder template = UriComponentsBuilder.newInstance();
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(base);
        URI redirectUri;
        try {
            // assume it's encoded to start with (if it came in over the wire)
            redirectUri = builder.build(true).toUri();
        } catch (Exception e) {
            // ... but allow client registrations to contain hard-coded non-encoded values
            redirectUri = builder.build().toUri();
            builder = UriComponentsBuilder.fromUri(redirectUri);
        }
        template.scheme(redirectUri.getScheme()).port(redirectUri.getPort())
                .host(redirectUri.getHost()).userInfo(redirectUri.getUserInfo())
                .path(redirectUri.getPath());

        if (fragment) {
            StringBuilder values = new StringBuilder();
            if (redirectUri.getFragment() != null) {
                String append = redirectUri.getFragment();
                values.append(append);
            }
            for (String key : query.keySet()) {
                if (values.length() > 0) {
                    values.append("&");
                }
                String name = key;
                if (keys != null && keys.containsKey(key)) {
                    name = keys.get(key);
                }
                values.append(name).append("={").append(key).append("}");
            }
            if (values.length() > 0) {
                template.fragment(values.toString());
            }
            UriComponents encoded = template.build().expand(query).encode();
            builder.fragment(encoded.getFragment());
        } else {
            for (String key : query.keySet()) {
                String name = key;
                if (keys != null && keys.containsKey(key)) {
                    name = keys.get(key);
                }
                template.queryParam(name, "{" + key + "}");
            }
            template.fragment(redirectUri.getFragment());
            UriComponents encoded = template.build().expand(query).encode();
            builder.query(encoded.getQuery());
        }

        return builder.build().toUriString();

    }

}
