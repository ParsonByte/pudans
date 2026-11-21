package cn.tolim.pudans.core.risk.reconciler;

import cn.tolim.pudans.contract.risk.RiskRule;
import cn.tolim.pudans.extension.contract.controller.Controller;
import cn.tolim.pudans.extension.contract.controller.ControllerBuilder;
import cn.tolim.pudans.extension.contract.controller.Reconciler;
import cn.tolim.pudans.core.risk.RiskRuleRegistry;
import org.springframework.stereotype.Component;

@Component
public class RiskRuleReconciler implements Reconciler<Reconciler.Request> {

    private final RiskRuleRegistry ruleRegistry;

    // 注入规则注册表，用于响应扩展变更。
    public RiskRuleReconciler(RiskRuleRegistry ruleRegistry) {
        this.ruleRegistry = ruleRegistry;
    }

    // 扩展变更时刷新风险规则。
    @Override
    public Result reconcile(Request request) {
        ruleRegistry.refresh();
        return Result.doNotRetry();
    }

    // 声明本协调器负责的风险规则扩展。
    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder.extension(new RiskRule()).build();
    }
}
