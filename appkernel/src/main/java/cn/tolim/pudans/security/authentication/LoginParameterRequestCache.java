package cn.tolim.pudans.security.authentication;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;


public interface LoginParameterRequestCache {


    // 在登录请求中暂存指定参数，供后续流程读取。
    Mono<Void> saveParameter(ServerWebExchange exchange, String parameterName);


    // 登录完成后清除已暂存参数，避免残留在会话中。
    Mono<Void> removeParameter(ServerWebExchange exchange, String parameterName);


    // 从会话读取此前暂存的参数值，不存在时返回空。
    Mono<String> getParameter(ServerWebExchange exchange, String parameterName);
}
