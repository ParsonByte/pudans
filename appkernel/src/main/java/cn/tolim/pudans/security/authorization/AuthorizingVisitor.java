package cn.tolim.pudans.security.authorization;

import cn.tolim.pudans.contract.rbac.Role;
import java.util.ArrayList;
import java.util.List;


public class AuthorizingVisitor implements RuleAccumulator {
    private final RbacRequestEvaluation requestEvaluation = new RbacRequestEvaluation();

    private final Attributes requestAttributes;
    private final List<Throwable> errors = new ArrayList<>(4);
    private boolean allowed;

    public AuthorizingVisitor(Attributes requestAttributes) {
        this.requestAttributes = requestAttributes;
    }

    @Override
    public boolean visit(String source, Role.PolicyRule rule, Throwable error) {
        if (rule != null && requestEvaluation.ruleAllows(requestAttributes, rule)) {
            this.allowed = true;
            return false;
        }
        if (error != null) {
            this.errors.add(error);
        }
        return true;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public List<Throwable> getErrors() {
        return errors;
    }
}
