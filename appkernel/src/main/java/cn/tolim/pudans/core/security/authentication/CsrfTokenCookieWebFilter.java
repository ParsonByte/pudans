package cn.tolim.pudans.core.security.authentication;

import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

// 安全方法请求也订阅令牌属性，让令牌落盘到 Cookie，供前端回填请求头
final class CsrfTokenCookieWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        Mono<CsrfToken> csrfToken = exchange.getAttribute(CsrfToken.class.getName());
        return csrfToken == null ? chain.filter(exchange) : csrfToken.then(chain.filter(exchange));
    }
}
