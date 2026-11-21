package cn.tolim.pudans.core.security.authorization;

import cn.tolim.pudans.core.rbac.Roles;
import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.core.rbac.TempGrants;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import reactor.core.publisher.Mono;

@Slf4j
public class RequestInfoAuthorizationManager implements ReactiveAuthorizationManager<AuthorizationContext> {

    private final AuthorizationRuleResolver ruleResolver;

    public RequestInfoAuthorizationManager(
            Roles roles, TempGrants tempGrants, AuditLog auditLog) {
        this.ruleResolver = new DefaultRuleResolver(roles, tempGrants, auditLog);
    }

    @Override
    public Mono<AuthorizationResult> authorize(Mono<Authentication> authentication, AuthorizationContext context) {
        var request = context.getExchange().getRequest();
        var requestInfo = RequestInfoFactory.INSTANCE.newRequestInfo(request);


        return authentication
                .filter(Authentication::isAuthenticated)
                .flatMap(auth -> ruleResolver.visitRules(auth, requestInfo, context.getExchange()))
                .doOnNext(visitor -> showErrorMessage(visitor.getErrors()))
                .map(AuthorizingVisitor::isAllowed)
                .defaultIfEmpty(false)
                .map(AuthorizationDecision::new);
    }

    private void showErrorMessage(List<Throwable> errors) {
        if (errors != null) {
            errors.forEach(error -> log.error("访问决策错误", error));
        }
    }
}
