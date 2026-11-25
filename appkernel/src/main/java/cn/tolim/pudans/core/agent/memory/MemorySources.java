package cn.tolim.pudans.core.agent.memory;

import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.agent.AgentMessageEntity;
import cn.tolim.pudans.infra.r2dbc.agent.AgentMessageRepository;
import cn.tolim.pudans.infra.r2dbc.agent.AgentSessionEntity;
import cn.tolim.pudans.infra.r2dbc.agent.AgentSessionRepository;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemorySourceEntity;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemorySourceRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MemorySources {

    private static final int MESSAGE_SCAN_LIMIT = 500;

    private static final String SOURCE_KIND = "agent_run";

    private final AgentSessionRepository sessions;

    private final AgentMessageRepository messages;

    private final MemorySourceRepository sources;

    public record RegisteredConversation(
            AgentSessionEntity session,
            List<AgentMessageEntity> messages,
            MemorySourceEntity source) {}

    public Mono<RegisteredConversation> registerRun(
            String ownerId, UUID sessionId, UUID runId) {
        if (sessionId == null || runId == null) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "memory-source-run-required",
                    "会话和运行标识不能为空。"));
        }
        return sessions
                .findById(sessionId)
                .filter(session -> ownerId.equals(session.getOwnerId()))
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, "memory-session-not-found", "会话不存在。")))
                .flatMap(session -> messages
                        .findBySessionIdOrderByCreatedAtDesc(
                                sessionId, PageRequest.of(0, MESSAGE_SCAN_LIMIT))
                        .filter(message -> runId.equals(message.getRunId()))
                        .sort(Comparator.comparing(AgentMessageEntity::getCreatedAt)
                                .thenComparing(AgentMessageEntity::getId))
                        .collectList()
                        .flatMap(runMessages -> {
                            if (runMessages.isEmpty()) {
                                return Mono.error(new ApiException(
                                        HttpStatus.NOT_FOUND,
                                        "memory-source-messages-empty",
                                        "运行中没有可索引的消息。"));
                            }
                            return source(
                                    session, runId, runMessages)
                                    .map(source -> new RegisteredConversation(
                                            session, runMessages, source));
                        }));
    }

    private Mono<MemorySourceEntity> source(
            AgentSessionEntity session,
            UUID runId,
            List<AgentMessageEntity> runMessages) {
        return sources
                .findSource(
                        session.getOwnerId(),
                        session.getWorkspaceId(),
                        SOURCE_KIND,
                        runId.toString())
                .switchIfEmpty(Mono.defer(() -> sources.insert(
                        sourceEntity(session, runId, runMessages))));
    }

    private static MemorySourceEntity sourceEntity(
            AgentSessionEntity session,
            UUID runId,
            List<AgentMessageEntity> runMessages) {
        var now = Instant.now();
        var ids = runMessages.stream().map(message -> message.getId().toString()).toList();
        var roles = runMessages.stream()
                .map(AgentMessageEntity::getRole)
                .distinct()
                .sorted()
                .toList();
        var metadataMessages = new ArrayList<Map<String, Object>>();
        for (var message : runMessages) {
            var reference = new LinkedHashMap<String, Object>();
            reference.put("id", message.getId());
            reference.put("role", message.getRole());
            reference.put("createdAt", message.getCreatedAt() == null
                    ? null
                    : message.getCreatedAt().toInstant());
            metadataMessages.add(reference);
        }
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("messageCount", runMessages.size());
        metadata.put("roles", roles);
        metadata.put("messages", metadataMessages);
        var source = new MemorySourceEntity();
        source.setId(UUID.randomUUID());
        source.setOwnerId(session.getOwnerId());
        source.setWorkspaceId(session.getWorkspaceId());
        source.setSourceKind(SOURCE_KIND);
        source.setSourceId(runId.toString());
        source.setSessionId(session.getId());
        source.setRunId(runId);
        source.setMessageStartId(runMessages.getFirst().getId());
        source.setMessageEndId(runMessages.getLast().getId());
        source.setOccurredAt(runMessages.getLast().getCreatedAt() == null
                ? now
                : runMessages.getLast().getCreatedAt().toInstant());
        source.setContentSummary("run messages=" + runMessages.size()
                + " roles=" + String.join(",", roles));
        source.setContentDigest(MemoryText.digest(ids));
        source.setStatus("indexed");
        source.setMetadata(metadata);
        source.setCreatedAt(now);
        return source;
    }
}
