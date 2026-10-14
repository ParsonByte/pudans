package cn.tolim.pudans.core.audit.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.contract.audit.TraceContext;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.infra.audit.AuditIdGenerator;

@Slf4j
@Component
@RequiredArgsConstructor
public class PluginInvocationAuditor {

    private static final String OPERATION = "PLUGIN_ACTION";

    private final AuditService auditService;

    public <T> Mono<T> audit(String extensionPoint, Object plugin, String username, Mono<T> invocation) {
        return Mono.deferContextual(context -> {
            var traceFlowId = TraceContext.resolve(context);
            var startedAt = System.currentTimeMillis();
            return invocation
                    .doOnSuccess(ignored -> record(
                            extensionPoint, plugin, username, traceFlowId, startedAt, "SUCCESS", null))
                    .doOnError(error -> record(
                            extensionPoint, plugin, username, traceFlowId, startedAt, "FAILURE", error));
        });
    }

    public <T> Flux<T> auditMany(String extensionPoint, Object plugin, String username, Flux<T> invocation) {
        return Flux.deferContextual(context -> {
            var traceFlowId = TraceContext.resolve(context);
            var startedAt = System.currentTimeMillis();
            return invocation
                    .doOnComplete(() -> record(
                            extensionPoint, plugin, username, traceFlowId, startedAt, "SUCCESS", null))
                    .doOnError(error -> record(
                            extensionPoint, plugin, username, traceFlowId, startedAt, "FAILURE", error));
        });
    }

    private void record(
            String extensionPoint,
            Object plugin,
            String username,
            String traceFlowId,
            long startedAt,
            String result,
            Throwable error) {
        try {
            var pluginClass = plugin.getClass().getName();
            var effectiveTraceFlowId = traceFlowId == null ? AuditIdGenerator.next() : traceFlowId;
            var auditRecord = auditService.newRecord(
                    AuditIdGenerator.next(),
                    username == null || username.isBlank() ? "plugin" : username,
                    OPERATION);
            auditRecord.setTraceFlowId(effectiveTraceFlowId);
            if (traceFlowId == null) {
                auditRecord.markSyntheticFlow();
            }
            auditRecord.setResourceType("Plugin");
            auditRecord.setResourceName(extensionPoint + ":" + pluginClass);
            auditRecord.setResult(result);
            auditRecord.setDurationMs(System.currentTimeMillis() - startedAt);
            auditRecord.setTimestamp(Instant.now());
            var context = new LinkedHashMap<String, Object>();
            context.put("extensionPoint", extensionPoint);
            context.put("plugin", pluginClass);
            if (username != null) {
                context.put("username", username);
            }
            if (error != null) {
                context.put("error", error.getClass().getName());
                context.put("message", error.getMessage());
            }
            auditRecord.setContext(context);
            auditRecord.setActionSnapshot(JsonUtils.objectToJson(context));
            auditService.record(auditRecord);
        } catch (RuntimeException e) {
            log.warn("Unable to record the plugin invocation audit", e);
        }
    }
}
