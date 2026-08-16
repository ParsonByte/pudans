package cn.tolim.pudans.contract.audit;

import cn.tolim.pudans.contract.core.extension.audit.AuditRecord;
import org.pf4j.ExtensionPoint;
import org.springframework.core.Ordered;
import reactor.core.publisher.Mono;

public interface AuditSink extends ExtensionPoint, Ordered {

    Mono<Void> accept(AuditRecord record);

    @Override
    default int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
