package cn.tolim.pudans.security.authentication.oauth2;

import org.springframework.security.core.Authentication;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;


public interface OAuth2LoginHandlerEnhancer {

    Mono<Void> loginSuccess(ServerWebExchange exchange, Authentication authentication);
}
