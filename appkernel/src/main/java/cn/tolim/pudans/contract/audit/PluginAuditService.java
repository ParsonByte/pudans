package cn.tolim.pudans.contract.audit;

import reactor.core.publisher.Mono;

public interface PluginAuditService {

    void record(PluginAuditRecord record);

    Mono<String> currentTraceFlowId();
}
