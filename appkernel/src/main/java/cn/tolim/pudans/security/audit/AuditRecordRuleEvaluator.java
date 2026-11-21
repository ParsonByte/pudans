package cn.tolim.pudans.security.audit;

import cn.tolim.pudans.contract.infra.ParameterSetting;
import cn.tolim.pudans.infra.setting.ParameterGroups;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AuditRecordRuleEvaluator {

    private final ParameterGroups parameterGroups;

    public Mono<Boolean> shouldRecord(String operation, String resourceType) {
        return parameterGroups
                .getAudit()
                .map(audit -> evaluate(audit.getAuditRecordRules(), operation, resourceType))
                .defaultIfEmpty(true)
                .onErrorResume(error -> Mono.just(true));
    }

    private static boolean evaluate(
            ParameterSetting.AuditRecordRules rules, String operation, String resourceType) {
        if (rules == null) {
            return true;
        }
        var operations = rules.getOperations();
        var resourceTypes = rules.getResourceTypes();
        if (CollectionUtils.isEmpty(operations) && CollectionUtils.isEmpty(resourceTypes)) {
            return true;
        }
        var matched = matches(operations, operation) || matches(resourceTypes, resourceType);
        if (rules.isWhitelist()) {
            return matched;
        }
        if (rules.isBlacklist()) {
            return !matched;
        }
        return true;
    }

    private static boolean matches(List<String> patterns, String value) {
        if (CollectionUtils.isEmpty(patterns) || value == null) {
            return false;
        }
        return patterns.stream().anyMatch(pattern -> pattern != null
                && ("*".equals(pattern) || pattern.equalsIgnoreCase(value)));
    }
}
