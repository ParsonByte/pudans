package cn.tolim.pudans.security.authentication;

import java.net.URI;
import java.util.Locale;
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

    // 将目标地址转换为应用内路径，保留查询串与片段。
    public static String uriInApplication(ServerHttpRequest request, URI uri) {
        return uriInApplication(request, uri, true);
    }

    // 转换应用内路径，并按需决定是否附加片段。
    public static String uriInApplication(ServerHttpRequest request, URI uri, boolean appendFragment) {
        var path = RequestPath.parse(uri, request.getPath().contextPath().value());
        var query = uri.getRawQuery();
        var fragment = uri.getRawFragment();
        return path.pathWithinApplication().value()
                + (query == null ? "" : "?" + query)
                + (fragment == null || !appendFragment ? "" : "#" + fragment);
    }

    // 优先保存合法的应用内回跳地址，否则交给默认缓存处理。
    @Override
    public Mono<Void> saveRequest(ServerWebExchange exchange) {
        var redirectUriQuery = exchange.getRequest().getQueryParams().getFirst(REDIRECT_URI_QUERY);
        if (StringUtils.isNotBlank(redirectUriQuery)) {
            URI redirectUri;
            try {
                redirectUri = URI.create(redirectUriQuery);
            } catch (IllegalArgumentException error) {
                return Mono.empty();
            }
            var redirectUriInApplication = uriInApplication(exchange.getRequest(), redirectUri);
            if (isSafeRedirect(redirectUriInApplication)) {
                return saveRedirectUri(exchange, redirectUriInApplication);
            }
            // 非法跳转目标不保存，登录后回落到默认地址，避免开放重定向。
            return Mono.empty();
        }
        return super.saveRequest(exchange);
    }

    // 片段回跳仅在匹配当前请求时移除缓存，其余交给默认逻辑。
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

    // 将回跳地址写入会话，供登录成功后跳转。
    private Mono<Void> saveRedirectUri(ServerWebExchange exchange, String redirectUriInApplication) {
        return exchange.getSession()
                .map(WebSession::getAttributes)
                .doOnNext(attributes -> attributes.put(this.sessionAttrName, redirectUriInApplication))
                .then();
    }

    // 只允许应用内路径，拒绝协议相对、反斜杠和控制字符形式。
    private static boolean isSafeRedirect(String redirect) {
        if (redirect == null || !redirect.startsWith("/") || redirect.startsWith("//")) {
            return false;
        }
        var lower = redirect.toLowerCase(Locale.ROOT);
        return !redirect.startsWith("/\\")
                && !lower.startsWith("/%2f")
                && !lower.startsWith("/%5c")
                && !redirect.contains("\\")
                && !redirect.contains("\r")
                && !redirect.contains("\n");
    }
}
