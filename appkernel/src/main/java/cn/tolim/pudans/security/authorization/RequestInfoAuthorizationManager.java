package cn.tolim.pudans.security.authorization;

import cn.tolim.pudans.contract.core.user.service.RoleService;
import cn.tolim.pudans.core.audit.service.AuditService;
import cn.tolim.pudans.core.rbac.service.RbacRuleEnricherService;
import cn.tolim.pudans.core.rbac.service.TempRoleService;
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
            RoleService roleService,
            TempRoleService tempRoleService,
            AuditService auditService,
            RbacRuleEnricherService rbacRuleEnricherService) {
        this.ruleResolver = new DefaultRuleResolver(
                roleService, tempRoleService, auditService, rbacRuleEnricherService);
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
            errors.forEach(error -> log.error("Access decision error", error));
        }
    }
}
