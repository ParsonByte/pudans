package cn.tolim.pudans.contract.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;


public interface LoginHandlerEnhancer {


    Mono<Void> onLoginSuccess(ServerWebExchange exchange, Authentication successfulAuthentication);


    Mono<Void> onLoginFailure(ServerWebExchange exchange, AuthenticationException exception);
}
