package cn.tolim.pudans.contract.risk;

import org.pf4j.ExtensionPoint;
import org.springframework.core.Ordered;
import reactor.core.publisher.Mono;

public interface RiskAlertChannel extends ExtensionPoint, Ordered {

    Mono<Void> send(RiskAlert alert);

    @Override
    default int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
