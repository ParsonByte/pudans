package cn.tolim.pudans.security.authorization;

import cn.tolim.pudans.contract.rbac.Role;
import java.util.ArrayList;
import java.util.List;


public class AuthorizingVisitor implements RuleAccumulator {
    private final RbacRequestEvaluation requestEvaluation = new RbacRequestEvaluation();

    private final Attributes requestAttributes;
    private final List<Throwable> errors = new ArrayList<>(4);
    private boolean allowed;

    // 持有同一份请求属性供各规则复用
    public AuthorizingVisitor(Attributes requestAttributes) {
        this.requestAttributes = requestAttributes;
    }

    // 命中允许规则即短路，否则累积错误继续
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

    // 判断是否已有规则允许该请求
    public boolean isAllowed() {
        return allowed;
    }

    // 暴露解析错误供调用方记录日志
    public List<Throwable> getErrors() {
        return errors;
    }
}
