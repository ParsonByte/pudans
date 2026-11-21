package cn.tolim.pudans.core.risk.reconciler;

import cn.tolim.pudans.contract.risk.RiskRule;
import cn.tolim.pudans.contract.extension.controller.Controller;
import cn.tolim.pudans.contract.extension.controller.ControllerBuilder;
import cn.tolim.pudans.contract.extension.controller.Reconciler;
import cn.tolim.pudans.core.risk.RiskRuleRegistry;
import org.springframework.stereotype.Component;

@Component
public class RiskRuleReconciler implements Reconciler<Reconciler.Request> {

    private final RiskRuleRegistry ruleRegistry;

    public RiskRuleReconciler(RiskRuleRegistry ruleRegistry) {
        this.ruleRegistry = ruleRegistry;
    }

    @Override
    public Result reconcile(Request request) {
        ruleRegistry.refresh();
        return Result.doNotRetry();
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder.extension(new RiskRule()).build();
    }
}
