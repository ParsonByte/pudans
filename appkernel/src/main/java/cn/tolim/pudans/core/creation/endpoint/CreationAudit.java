package cn.tolim.pudans.core.creation.endpoint;

import cn.tolim.pudans.core.audit.AuditLog;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreationAudit {

    private final AuditLog auditLog;

    public Mono<Void> record(
            ServerRequest request,
            String operation,
            String resourceType,
            String resourceName,
            Map<String, Object> snapshot) {
        return auditLog
                .recordChild(
                        request.exchange(),
                        null,
                        operation,
                        resourceType,
                        resourceName,
                        "SUCCESS",
                        snapshot,
                        null)
                .onErrorResume(error -> {
                    log.warn("记录创作内容审计失败：{}", error.getMessage());
                    return Mono.empty();
                });
    }
}
