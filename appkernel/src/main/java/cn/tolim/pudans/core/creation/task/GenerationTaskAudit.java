package cn.tolim.pudans.core.creation.task;

import cn.tolim.pudans.contract.audit.AuditRecord;
import cn.tolim.pudans.contract.creation.GenerationTask;
import cn.tolim.pudans.contract.creation.dto.GenerationTaskSummary;
import cn.tolim.pudans.core.audit.AuditLog;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GenerationTaskAudit {

    private final AuditLog auditLog;

    public GenerationTaskAudit(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    // 请求内的启动、取消和重试审计复用统一审计链路。
    public Mono<Void> record(
            ServerWebExchange exchange,
            String operation,
            GenerationTask task,
            boolean success,
            Map<String, Object> extra) {
        return record(exchange, operation, summary(task), success, extra);
    }

    // 请求响应只含摘要时同样记录统一审计字段。
    public Mono<Void> record(
            ServerWebExchange exchange,
            String operation,
            GenerationTaskSummary task,
            boolean success,
            Map<String, Object> extra) {
        var context = new LinkedHashMap<String, Object>();
        context.put("taskId", task.id());
        context.put("status", task.status().getCode());
        context.put("mediaType", task.mediaType());
        context.put("operation", task.operation());
        context.put("modelId", task.modelId());
        context.put("providerRequestSent", task.providerRequestSent());
        if (extra != null) {
            context.putAll(extra);
        }
        return auditLog.recordChild(
                exchange,
                null,
                operation,
                "GenerationTask",
                task.id().toString(),
                success ? "SUCCESS" : "FAILURE",
                context,
                null);
    }

    private static GenerationTaskSummary summary(GenerationTask task) {
        return GenerationTasks.summary(task);
    }

    // 后台供应商失败没有请求上下文，使用任务自身作为追踪来源。
    public void providerFailure(GenerationTask task, String errorCode) {
        var record = auditLog.newRecord(task.id().toString(), task.ownerId(), "GENERATION_TASK_PROVIDER_FAILURE");
        record.setResourceType("GenerationTask");
        record.setResourceName(task.id().toString());
        record.setResult("FAILURE");
        var context = new LinkedHashMap<String, Object>();
        context.put("taskId", task.id());
        context.put("modelId", task.modelId());
        context.put("errorCode", errorCode);
        record.setContext(context);
        auditLog.record(record);
    }

    // 记录成功归档结果，不包含提示词和对象地址。
    public void completed(GenerationTask task, int storageFileCount, boolean assetsRegistered) {
        var record = auditLog.newRecord(task.id().toString(), task.ownerId(), "GENERATION_TASK_COMPLETED");
        record.setResourceType("GenerationTask");
        record.setResourceName(task.id().toString());
        record.setResult("SUCCESS");
        var context = new LinkedHashMap<String, Object>();
        context.put("taskId", task.id());
        context.put("modelId", task.modelId());
        context.put("storageFileCount", storageFileCount);
        context.put("assetsRegistered", assetsRegistered);
        record.setContext(context);
        auditLog.record(record);
    }
}
