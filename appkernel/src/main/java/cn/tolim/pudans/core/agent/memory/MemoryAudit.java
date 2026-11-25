package cn.tolim.pudans.core.agent.memory;

import cn.tolim.pudans.contract.audit.AuditRecord;
import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MemoryAudit {

    private final AuditLog auditLog;

    public Mono<Void> record(
            ServerWebExchange exchange,
            RequestIdentity identity,
            String operation,
            String resourceType,
            UUID resourceId,
            boolean success,
            Map<String, Object> extra) {
        return auditLog
                .recordChild(
                        exchange,
                        identity.ownerId(),
                        operation,
                        resourceType,
                        resourceId.toString(),
                        success ? "SUCCESS" : "FAILURE",
                        context(resourceId, extra),
                        null)
                .switchIfEmpty(Mono.fromRunnable(() -> background(
                        identity.ownerId(), operation, resourceType, resourceId, success, extra)))
                .then();
    }

    public void background(
            String ownerId,
            String operation,
            String resourceType,
            UUID resourceId,
            boolean success,
            Map<String, Object> extra) {
        var record = auditLog.newRecord(resourceId.toString(), ownerId, operation);
        record.setResourceType(resourceType);
        record.setResourceName(resourceId.toString());
        record.setResult(success ? "SUCCESS" : "FAILURE");
        record.setContext(context(resourceId, extra));
        auditLog.record(record);
    }

    private static Map<String, Object> context(UUID resourceId, Map<String, Object> extra) {
        var context = new LinkedHashMap<String, Object>();
        context.put("memoryId", resourceId);
        if (extra != null) {
            context.putAll(extra);
        }
        return context;
    }
}
