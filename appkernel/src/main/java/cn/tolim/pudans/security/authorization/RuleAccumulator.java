package cn.tolim.pudans.security.authorization;

import cn.tolim.pudans.contract.core.extension.rbac.Role;


public interface RuleAccumulator {
    boolean visit(String source, Role.PolicyRule rule, Throwable err);
}
