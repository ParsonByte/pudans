package cn.tolim.pudans.core.security.authorization;

import cn.tolim.pudans.contract.rbac.Role;


public interface RuleAccumulator {
    boolean visit(String source, Role.PolicyRule rule, Throwable err);
}
