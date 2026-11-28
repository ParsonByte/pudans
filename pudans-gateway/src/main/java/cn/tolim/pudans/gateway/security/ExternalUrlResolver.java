package cn.tolim.pudans.gateway.security;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.net.URI;

public final class ExternalUrlResolver {

    public static final String FORWARDED_PROTO_HEADER = "X-Forwarded-Proto";
    public static final String FORWARDED_HOST_HEADER = "X-Forwarded-Host";
    public static final String FORWARDED_PORT_HEADER = "X-Forwarded-Port";
    private static final String DEFAULT_SCHEME = "http";
    private static final String DEFAULT_AUTHORITY = "localhost";

    private final boolean trustForwardedHeaders;

    public ExternalUrlResolver(boolean trustForwardedHeaders) {
        this.trustForwardedHeaders = trustForwardedHeaders;
    }

    public String resolve(ServerHttpRequest request) {
        URI uri = request.getURI();
        String scheme = sanitize(uri.getScheme());
        String authority = sanitize(uri.getRawAuthority());
        if (trustForwardedHeaders) {
            String forwardedProto = firstHeader(request, FORWARDED_PROTO_HEADER);
            if (forwardedProto != null) {
                scheme = forwardedProto;
            }
            String forwardedHost = firstHeader(request, FORWARDED_HOST_HEADER);
            if (forwardedHost != null) {
                authority = forwardedHost;
            }
            String forwardedPort = firstHeader(request, FORWARDED_PORT_HEADER);
            if (forwardedPort != null && (authority == null || !hasExplicitPort(authority))) {
                authority = authority == null ? DEFAULT_AUTHORITY : authority;
                authority = authority + ":" + forwardedPort;
            }
        }
        if (scheme == null) {
            scheme = DEFAULT_SCHEME;
        }
        if (authority == null) {
            authority = sanitize(request.getHeaders().getFirst(HttpHeaders.HOST));
        }
        if (authority == null) {
            authority = DEFAULT_AUTHORITY;
        }
        String path = uri.getRawPath();
        if (path == null || path.isBlank()) {
            path = "/";
        }
        String query = uri.getRawQuery();
        return scheme + "://" + authority + path + (query == null || query.isBlank() ? "" : "?" + query);
    }

    private static String firstHeader(ServerHttpRequest request, String name) {
        String value = request.getHeaders().getFirst(name);
        if (value == null) {
            return null;
        }
        int comma = value.indexOf(',');
        String first = comma < 0 ? value : value.substring(0, comma);
        return sanitize(first);
    }

    private static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.indexOf('\r') >= 0 || trimmed.indexOf('\n') >= 0) {
            return null;
        }
        return trimmed;
    }

    private static boolean hasExplicitPort(String authority) {
        if (authority.startsWith("[")) {
            int closingBracket = authority.indexOf(']');
            return closingBracket >= 0
                    && closingBracket + 1 < authority.length()
                    && authority.charAt(closingBracket + 1) == ':';
        }
        return authority.lastIndexOf(':') >= 0;
    }
}