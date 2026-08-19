package cn.tolim.pudans.contract.security;

import cn.tolim.pudans.contract.core.extension.rbac.Role.PolicyRule;
import java.util.List;
import org.pf4j.ExtensionPoint;
import org.springframework.core.Ordered;
import reactor.core.publisher.Flux;

public interface RbacRuleEnricher extends ExtensionPoint, Ordered {

    Flux<PolicyRule> enrich(String username, List<String> roleNames);

    @Override
    default int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
