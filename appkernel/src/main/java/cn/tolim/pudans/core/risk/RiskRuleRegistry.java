package cn.tolim.pudans.core.risk;

import static cn.tolim.pudans.extension.contract.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.extension.contract.ExtensionUtil.notDeleting;

import cn.tolim.pudans.contract.risk.RiskRule;
import cn.tolim.pudans.extension.contract.ExtensionClient;
import cn.tolim.pudans.extension.contract.ListOptions;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class RiskRuleRegistry {

    private final ExtensionClient client;

    private volatile Map<String, RiskRule.Spec> rules = defaults();

    // 提供内置风险规则，作为扩展配置缺失时的兜底。
    public static Map<String, RiskRule.Spec> defaults() {
        return Map.ofEntries(
                Map.entry(RiskScene.REQUEST_FLOOD, rule(RiskScene.REQUEST_FLOOD, "Per-IP request flood", 60, 300, 60)),
                Map.entry(RiskScene.LOGIN_ATTEMPT, rule(RiskScene.LOGIN_ATTEMPT, "Per-IP login attempt", 60, 5, 60)),
                Map.entry(RiskScene.LOGIN_FAILURE,
                        rule(RiskScene.LOGIN_FAILURE, "Per-IP consecutive login failure", 60, 5, 300)),
                Map.entry(RiskScene.TOTP_ATTEMPT,
                        rule(RiskScene.TOTP_ATTEMPT, "Per-session TOTP validation", 300, 5, 300)),
                Map.entry(RiskScene.AUTH_FAILURE,
                        rule(RiskScene.AUTH_FAILURE, "Per-IP authentication failure", 300, 20, 0)),
                Map.entry(RiskScene.SERVER_ERROR, rule(RiskScene.SERVER_ERROR, "Per-IP server error", 300, 10, 0)),
                Map.entry(RiskScene.SUDO_SEND, rule(RiskScene.SUDO_SEND, "Per-user sudo code sending", 300, 5, 300)),
                Map.entry(RiskScene.SUDO_CONFIRM,
                        rule(RiskScene.SUDO_CONFIRM, "Per-user sudo confirmation", 300, 5, 300)));
    }

    // 按阈值决定规则的封禁或告警动作。
    private static RiskRule.Spec rule(String scene, String description, long windowSeconds, long threshold,
            long blockSeconds) {
        var spec = new RiskRule.Spec();
        spec.setScene(scene);
        spec.setDescription(description);
        spec.setWindowSeconds(windowSeconds);
        spec.setThreshold(threshold);
        spec.setBlockSeconds(blockSeconds);
        spec.setAction(blockSeconds > 0 ? RiskRule.ACTION_BLOCK : RiskRule.ACTION_ALERT);
        return spec;
    }

    // 按场景读取当前生效的风险规则。
    public RiskRule.Spec rule(String scene) {
        return rules.get(scene);
    }

    // 返回当前生效的规则数量。
    public int ruleCount() {
        return rules.size();
    }

    // 返回已启用规则对应的场景集合。
    public java.util.Set<String> enabledScenes() {
        return rules.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue().isEnabled())
                .map(java.util.Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
    }

    // 从扩展服务重载规则，失败时保留旧配置。
    public void refresh() {
        try {
            var loaded = client.listAll(
                    RiskRule.class,
                    ListOptions.builder().andQuery(notDeleting()).build(),
                    defaultSort());
            var merged = new HashMap<>(defaults());
            for (var rule : loaded) {
                var spec = rule.getSpec();
                if (spec != null && StringUtils.hasText(spec.getScene())) {
                    merged.put(spec.getScene(), spec);
                }
            }
            rules = Map.copyOf(merged);
        } catch (RuntimeException e) {
            log.warn("无法刷新风险规则，保留之前的规则", e);
        }
    }
}
