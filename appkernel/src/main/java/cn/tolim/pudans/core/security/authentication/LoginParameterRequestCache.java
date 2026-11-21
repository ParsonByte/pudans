package cn.tolim.pudans.core.security.authentication;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;


public interface LoginParameterRequestCache {


    Mono<Void> saveParameter(ServerWebExchange exchange, String parameterName);


    Mono<Void> removeParameter(ServerWebExchange exchange, String parameterName);


    Mono<String> getParameter(ServerWebExchange exchange, String parameterName);
}
