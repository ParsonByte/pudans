package cn.tolim.pudans.core.rbac.service;

import cn.tolim.pudans.contract.core.extension.rbac.Role.PolicyRule;
import cn.tolim.pudans.contract.plugin.extensionpoint.ExtensionGetter;
import cn.tolim.pudans.contract.security.RbacRuleEnricher;
import cn.tolim.pudans.core.audit.service.PluginInvocationAuditor;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class RbacRuleEnricherService {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final ExtensionGetter extensionGetter;

    private final RbacCacheService rbacCacheService;

    private final RbacEnricherSignature enricherSignature;

    private final PluginInvocationAuditor pluginInvocationAuditor;

    public Mono<List<PolicyRule>> enrich(String username, Set<String> roleNames) {
        if (username == null || roleNames == null || roleNames.isEmpty()) {
            return Mono.just(List.of());
        }
        if (RbacEnricherSignature.EMPTY.equals(enricherSignature.signature())) {
            return Mono.just(List.of());
        }
        return rbacCacheService.cachedEnrichedRules(username, roleNames, load(username, roleNames));
    }

    private Mono<List<PolicyRule>> load(String username, Set<String> roleNames) {
        var names = List.copyOf(roleNames);
        return extensionGetter
                .getEnabledExtensions(RbacRuleEnricher.class)
                .concatMap(enricher -> pluginInvocationAuditor.auditMany(
                        "rbac-rule-enricher",
                        enricher,
                        username,
                        Flux.defer(() -> enricher.enrich(username, names)).timeout(TIMEOUT)))
                .collect(Collectors.toCollection(TreeSet::new))
                .map(sorted -> List.copyOf(sorted))
                .doOnError(error -> log.warn("RBAC rule enrichment failed, denying the request", error));
    }
}
