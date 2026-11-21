package cn.tolim.pudans.security.authentication.rememberme;

import org.springframework.security.core.Authentication;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public interface RememberMeServices {

    // 尝试用记住我 Cookie 自动认证，无 Cookie 时返回空。
    Mono<Authentication> autoLogin(ServerWebExchange exchange);

    // 登录失败时清理 Cookie 并暂存请求参数。
    Mono<Void> loginFail(ServerWebExchange exchange);

    // 登录成功后按用户意愿决定是否签发记住我 Cookie。
    Mono<Void> loginSuccess(ServerWebExchange exchange, Authentication successfulAuthentication);
}
