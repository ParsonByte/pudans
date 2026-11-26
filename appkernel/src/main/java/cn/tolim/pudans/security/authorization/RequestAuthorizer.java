package cn.tolim.pudans.security.authorization;

import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.core.rbac.Roles;
import cn.tolim.pudans.core.rbac.TempGrants;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class RequestAuthorizer {

    private final AuthorizationRuleResolver resolver;

    public RequestAuthorizer(Roles roles, TempGrants tempGrants, AuditLog auditLog) {
        this.resolver = new DefaultRuleResolver(roles, tempGrants, auditLog);
    }

    // 复用同一套角色规则，为无法经过 HTTP 过滤链的动作补充授权判定。
    public Mono<Boolean> allows(Authentication authentication, RequestInfo requestInfo) {
        return resolver
                .visitRules(authentication, requestInfo)
                .map(AuthorizingVisitor::isAllowed);
    }
}
