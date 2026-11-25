package cn.tolim.pudans.core.agent.sandbox;

import cn.tolim.pudans.contract.agent.Sandbox;
import cn.tolim.pudans.core.audit.AuditLog;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class SandboxAudit {

    private final AuditLog auditLog;

    // 沙箱操作复用请求审计链路，不记录命令正文和输出。
    public Mono<Void> record(
            ServerRequest request,
            String operation,
            Sandbox sandbox,
            boolean success,
            Map<String, Object> extra) {
        var context = new LinkedHashMap<String, Object>();
        context.put("sandboxId", sandbox.id());
        context.put("status", sandbox.status().getCode());
        context.put("provider", sandbox.provider());
        context.put("agentSessionId", sandbox.agentSessionId());
        if (sandbox.runId() != null) {
            context.put("runId", sandbox.runId());
        }
        if (extra != null) {
            context.putAll(extra);
        }
        return auditLog
                .recordChild(
                        request.exchange(),
                        null,
                        operation,
                        "Sandbox",
                        sandbox.id().toString(),
                        success ? "SUCCESS" : "FAILURE",
                        context,
                        null)
                .onErrorResume(error -> {
                    log.warn("记录沙箱审计失败：{}", error.getMessage());
                    return Mono.empty();
                });
    }

    // 失败请求仍记录资源标识和稳定错误码。
    public Mono<Void> failure(
            ServerRequest request,
            String operation,
            String sandboxId,
            String errorCode) {
        var context = new LinkedHashMap<String, Object>();
        context.put("sandboxId", sandboxId);
        context.put("errorCode", errorCode);
        return auditLog
                .recordChild(
                        request.exchange(),
                        null,
                        operation,
                        "Sandbox",
                        sandboxId,
                        "FAILURE",
                        context,
                        null)
                .onErrorResume(error -> {
                    log.warn("记录沙箱失败审计失败：{}", error.getMessage());
                    return Mono.empty();
                });
    }
}
