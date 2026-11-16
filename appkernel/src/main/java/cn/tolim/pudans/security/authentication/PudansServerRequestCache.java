package cn.tolim.pudans.security.authentication;

import java.net.URI;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.web.server.savedrequest.WebSessionServerRequestCache;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;


public class PudansServerRequestCache extends WebSessionServerRequestCache {


    private static final String DEFAULT_SAVED_REQUEST_ATTR = "SPRING_SECURITY_SAVED_REQUEST";

    private static final String REDIRECT_URI_QUERY = "redirect_uri";

    private final String sessionAttrName = DEFAULT_SAVED_REQUEST_ATTR;

    public static String uriInApplication(ServerHttpRequest request, URI uri) {
        return uriInApplication(request, uri, true);
    }

    public static String uriInApplication(ServerHttpRequest request, URI uri, boolean appendFragment) {
        var path = RequestPath.parse(uri, request.getPath().contextPath().value());
        var query = uri.getRawQuery();
        var fragment = uri.getRawFragment();
        return path.pathWithinApplication().value()
                + (query == null ? "" : "?" + query)
                + (fragment == null || !appendFragment ? "" : "#" + fragment);
    }

    @Override
    public Mono<Void> saveRequest(ServerWebExchange exchange) {
        var redirectUriQuery = exchange.getRequest().getQueryParams().getFirst(REDIRECT_URI_QUERY);
        if (StringUtils.isNotBlank(redirectUriQuery)) {

            var redirectUri = URI.create(redirectUriQuery);
            return saveRedirectUri(exchange, redirectUri);
        }
        return super.saveRequest(exchange);
    }

    @Override
    public Mono<URI> getRedirectUri(ServerWebExchange exchange) {
        return super.getRedirectUri(exchange);
    }

    @Override
    public Mono<ServerHttpRequest> removeMatchingRequest(ServerWebExchange exchange) {
        return getRedirectUri(exchange).flatMap(redirectUri -> {
            if (redirectUri.getFragment() != null) {
                var redirectUriInApplication = uriInApplication(exchange.getRequest(), redirectUri, false);
                var uriInApplication = uriInApplication(
                        exchange.getRequest(), exchange.getRequest().getURI());

                if (!Objects.equals(redirectUriInApplication, uriInApplication)) {
                    return Mono.empty();
                }

                return exchange.getSession()
                        .map(WebSession::getAttributes)
                        .doOnNext(attributes -> attributes.remove(this.sessionAttrName))
                        .thenReturn(exchange.getRequest());
            }
            return super.removeMatchingRequest(exchange);
        });
    }

    private Mono<Void> saveRedirectUri(ServerWebExchange exchange, URI redirectUri) {
        var redirectUriInApplication = uriInApplication(exchange.getRequest(), redirectUri);
        return exchange.getSession()
                .map(WebSession::getAttributes)
                .doOnNext(attributes -> attributes.put(this.sessionAttrName, redirectUriInApplication))
                .then();
    }
}
