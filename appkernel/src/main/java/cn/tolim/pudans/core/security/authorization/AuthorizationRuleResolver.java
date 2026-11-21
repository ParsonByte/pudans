package cn.tolim.pudans.core.security.authorization;

import org.springframework.security.core.Authentication;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public interface AuthorizationRuleResolver {

    Mono<AuthorizingVisitor> visitRules(Authentication authentication, RequestInfo requestInfo);

    default Mono<AuthorizingVisitor> visitRules(
            Authentication authentication, RequestInfo requestInfo, ServerWebExchange exchange) {
        return visitRules(authentication, requestInfo);
    }
}
