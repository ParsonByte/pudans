package cn.tolim.pudans.security.authorization;

import cn.tolim.pudans.contract.rbac.Role;


public interface RuleAccumulator {
    // 返回 true 表示继续收集后续规则
    boolean visit(String source, Role.PolicyRule rule, Throwable err);
}
