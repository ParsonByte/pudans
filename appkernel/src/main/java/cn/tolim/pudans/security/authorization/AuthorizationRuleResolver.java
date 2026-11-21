package cn.tolim.pudans.security.authorization;

import org.springframework.security.core.Authentication;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public interface AuthorizationRuleResolver {

    // 依据认证与请求信息累计命中的规则
    Mono<AuthorizingVisitor> visitRules(Authentication authentication, RequestInfo requestInfo);

    // 默认忽略请求上下文，沿用基础解析
    default Mono<AuthorizingVisitor> visitRules(
            Authentication authentication, RequestInfo requestInfo, ServerWebExchange exchange) {
        return visitRules(authentication, requestInfo);
    }
}
