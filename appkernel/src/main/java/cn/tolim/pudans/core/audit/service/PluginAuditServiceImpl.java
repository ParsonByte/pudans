package cn.tolim.pudans.core.audit.service;

import cn.tolim.pudans.contract.audit.PluginAuditRecord;
import cn.tolim.pudans.contract.audit.PluginAuditService;
import cn.tolim.pudans.contract.audit.TraceContext;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.infra.audit.AuditIdGenerator;
import java.time.Instant;
import java.util.LinkedHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class PluginAuditServiceImpl implements PluginAuditService {

    private final AuditService auditService;

    private static String resolveResourceType(PluginAuditRecord record) {
        if (record.resourceType() != null) {
            return record.resourceType();
        }
        if (record.pluginId() == null) {
            return "Plugin";
        }
        return "plugin:" + record.pluginId();
    }

    @Override
    public void record(PluginAuditRecord record) {
        if (record == null) {
            return;
        }
        var traceFlowId = record.traceFlowId() == null ? AuditIdGenerator.next() : record.traceFlowId();
        var operation = record.operation() == null ? "PLUGIN_ACTION" : record.operation();
        var auditRecord = auditService.newRecord(
                AuditIdGenerator.next(),
                record.username() == null || record.username().isBlank() ? "plugin" : record.username(),
                operation);
        auditRecord.setTraceFlowId(traceFlowId);
        auditRecord.setResourceType(resolveResourceType(record));
        auditRecord.setResourceName(record.resourceName());
        auditRecord.setResult(record.result() == null ? "SUCCESS" : record.result());
        auditRecord.setDurationMs(Math.max(0L, record.durationMs()));
        auditRecord.setTimestamp(Instant.now());

        var context = new LinkedHashMap<String, Object>();
        if (record.pluginId() != null) {
            context.put("pluginId", record.pluginId());
        }
        if (record.context() != null) {
            context.putAll(record.context());
        }
        auditRecord.setContext(context);
        auditRecord.setActionSnapshot(JsonUtils.objectToJson(context));
        auditService.record(auditRecord);
    }

    @Override
    public Mono<String> currentTraceFlowId() {
        return TraceContext.currentTraceFlowId();
    }
}
