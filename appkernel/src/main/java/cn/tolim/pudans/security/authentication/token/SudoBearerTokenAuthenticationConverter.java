package cn.tolim.pudans.security.authentication.token;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

final class SudoBearerTokenAuthenticationConverter implements ServerAuthenticationConverter {

    static final String SUDO_HEADER = "X-Pudans-Sudo-User";

    private final ServerBearerTokenAuthenticationConverter delegate = new ServerBearerTokenAuthenticationConverter();

    // 读取请求头中的代操作用户名并包装为专用令牌。
    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        var sudoUsername = exchange.getRequest().getHeaders().getFirst(SUDO_HEADER);
        return delegate.convert(exchange).map(authentication -> {
            if (authentication instanceof BearerTokenAuthenticationToken token
                    && StringUtils.hasText(sudoUsername)) {
                return new SudoBearerTokenAuthenticationToken(token.getToken(), sudoUsername.trim());
            }
            return authentication;
        });
    }
}
