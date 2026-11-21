package cn.tolim.pudans.core.ai.memory.governance;

import cn.tolim.pudans.contract.ai.AiSetting;
import cn.tolim.pudans.infra.api.utils.JsonUtils;
import cn.tolim.pudans.core.ai.config.AiSettings;
import cn.tolim.pudans.core.ai.memory.L1MemoryRecord;
import cn.tolim.pudans.core.ai.memory.MemoryStatus;
import cn.tolim.pudans.core.ai.memory.MemoryType;
import cn.tolim.pudans.core.ai.memory.task.MemoryTaskScheduler;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository.Conversation;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiMessageRepository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryGovernanceRepository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryGovernanceRepository.MemoryRequestRecord;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryGovernanceRepository.NewMemoryRequest;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryGovernanceRepository.TaskStatusCount;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class MemoryGovernance {

    public static final String CODE_FORBIDDEN = "ai-memory-forbidden";

    public static final String CODE_NOT_FOUND = "ai-memory-not-found";

    public static final String CODE_DELETE_FORBIDDEN = "ai-memory-delete-forbidden";

    public static final String CODE_REQUEST_INVALID = "ai-memory-request-invalid";

    public static final String CODE_REQUEST_CONFLICT = "ai-memory-request-conflict";

    private static final String MEMORY_LEVEL_L1 = "L1";

    private static final String CODE_CONVERSATION_NOT_FOUND = "ai-conversation-not-found";

    private static final int TASK_PENDING = 0;

    private static final int TASK_RUNNING = 1;

    private static final int TASK_DONE = 2;

    private static final int TASK_FAILED = 3;

    private final AiMemoryGovernanceRepository repository;

    private final AiSettings aiSettings;

    private final AiConversationRepository conversationRepository;

    private final AiMessageRepository messageRepository;

    private final MemoryTaskScheduler memoryTaskScheduler;

    public Mono<List<L1MemoryRecord>> list(
            @Nullable String teamId,
            @Nullable String agentId,
            @Nullable String userId,
            @Nullable MemoryType type,
            @Nullable MemoryStatus status,
            long beforeId,
            int limit) {
        return repository.listL1(teamId, agentId, userId, type, status, beforeId, limit).collectList();
    }

    public Mono<L1MemoryRecord> get(long id) {
        return repository.findL1ById(id).switchIfEmpty(Mono.error(notFound("记忆不存在。")));
    }

    public Mono<L1MemoryRecord> freeze(long id, String actor) {
        return transition(id, MemoryStatus.ACTIVE, MemoryStatus.FROZEN, "只有 ACTIVE 记忆可以冻结。");
    }

    public Mono<L1MemoryRecord> unfreeze(long id, String actor) {
        return transition(id, MemoryStatus.FROZEN, MemoryStatus.ACTIVE, "只有 FROZEN 记忆可以解冻。");
    }

    public Mono<L1MemoryRecord> delete(long id, String reason, String actor) {
        if (!StringUtils.hasText(reason)) {
            return Mono.error(invalid("删除原因不能为空。"));
        }
        return repository.findL1ById(id).switchIfEmpty(Mono.error(notFound("记忆不存在。"))).flatMap(memory -> {
            if (memory.status() == MemoryStatus.DELETED) {
                return Mono.error(new ApiException(
                        HttpStatus.FORBIDDEN, CODE_DELETE_FORBIDDEN, "记忆已删除，不能重复删除。"));
            }
            return memorySettings().flatMap(settings -> snapshotAndDelete(memory, reason.strip(), actor, settings));
        });
    }

    public Mono<L1MemoryRecord> restore(long id, String actor) {
        return repository.findL1ById(id)
                .switchIfEmpty(Mono.error(notFound("记忆不存在。")))
                .flatMap(found -> repository.findLatestSnapshot(found.id())
                        .switchIfEmpty(Mono.error(notFound("记忆快照不存在。")))
                        .flatMap(snapshot -> {
                            var record = JsonUtils.jsonToObject(snapshot, SnapshotPayload.class).record();
                            return repository.updateL1ContentAndStatus(
                                    found.id(), record.content(), record.metadata(), MemoryStatus.ACTIVE);
                        })
                        .switchIfEmpty(Mono.error(notFound("记忆不存在。"))));
    }

    public Mono<MemoryRequestRecord> createRequest(
            String userId,
            String name,
            MemoryRequestType requestType,
            String reason,
            String suggestedContent) {
        if (requestType == null) {
            return Mono.error(invalid("申请类型不能为空。"));
        }
        var memoryId = parseId(name);
        return repository
                .findL1ById(memoryId)
                .switchIfEmpty(Mono.error(notFound("记忆不存在。")))
                .flatMap(memory -> {
            if (!userId.equals(memory.context().userId())) {
                return Mono.error(new ApiException(
                        HttpStatus.FORBIDDEN, CODE_FORBIDDEN, "只能对自己的记忆提交申请。"));
            }
            if (memory.status() == MemoryStatus.DELETED || memory.status() == MemoryStatus.SUPERSEDED) {
                return Mono.error(invalid("该记忆当前状态不支持申请。"));
            }
            if (requestType == MemoryRequestType.CORRECT && !StringUtils.hasText(suggestedContent)) {
                return Mono.error(invalid("更正申请必须提供建议内容。"));
            }
            return memorySettings().flatMap(settings -> {
                var minimum = settings.getRequestMinReasonLength();
                var length = reason == null ? 0 : reason.strip().length();
                if (!StringUtils.hasText(reason) || length < minimum) {
                    return Mono.error(invalid("申请理由至少需要 " + minimum + " 个字符。"));
                }
                return repository
                        .existsPendingRequest(userId, MEMORY_LEVEL_L1, memoryId, requestType.name())
                        .flatMap(exists -> Boolean.TRUE.equals(exists)
                                ? Mono.error(conflict("该记忆已有待处理的同类申请。"))
                                : submitRequest(userId, memory, requestType, reason.strip(), suggestedContent));
            });
        });
    }

    public Flux<MemoryRequestRecord> listRequests(
            @Nullable String userId, @Nullable MemoryRequestStatus status, long beforeId, int limit) {
        return repository.listRequests(userId, status == null ? null : status.name(), beforeId, limit);
    }

    public Mono<MemoryRequestRecord> approve(long requestId, String reviewer, String comment) {
        return findPendingRequest(requestId).flatMap(request -> {
            var applied = switch (request.requestType()) {
                case "DELETE" -> deleteRecord(request.memoryId(), request.reason(), reviewer);
                case "CORRECT" -> correctRecord(request.memoryId(), request.suggestedContent());
                default -> Mono.<L1MemoryRecord>error(invalid("申请类型不合法。"));
            };
            return applied.then(completeRequest(
                    request.id(), MemoryRequestStatus.APPROVED, reviewer, comment));
        });
    }

    public Mono<MemoryRequestRecord> reject(long requestId, String reviewer, String comment) {
        if (!StringUtils.hasText(comment)) {
            return Mono.error(invalid("驳回意见不能为空。"));
        }
        return findPendingRequest(requestId).flatMap(request -> repository
                .updateRequestStatus(request.id(), MemoryRequestStatus.REJECTED.name(), reviewer, comment.strip())
                .switchIfEmpty(Mono.error(invalid("申请已处理。")))
                .flatMap(updated -> releaseCorrection(updated.memoryId()).thenReturn(updated)));
    }

    public Mono<PipelineStats> pipelineStats() {
        return Mono.zip(
                        repository.taskStatusCounts().collectList(),
                        repository.sumConversationPendingCount().defaultIfEmpty(0L))
                .map(tuple -> stats(tuple.getT1(), tuple.getT2()));
    }

    public Mono<Void> flush(long conversationId, String actor) {
        if (conversationId <= 0) {
            return Mono.error(invalid("会话标识必须大于零。"));
        }
        return conversationRepository
                .findById(conversationId)
                .switchIfEmpty(Mono.error(conversationNotFound()))
                .flatMap(conversation -> messageRepository
                        .findLatestId(conversationId)
                        .flatMap(lastMessageId -> lastMessageId <= 0
                                ? Mono.error(invalid("会话没有可处理的消息。"))
                                : memoryTaskScheduler.enqueueExtraction(
                                        conversation, conversation.userId(), lastMessageId)));
    }

    public Mono<RebuildResult> rebuild(
            @Nullable Long conversationId,
            @Nullable String teamId,
            @Nullable String agentId,
            @Nullable String level,
            @Nullable Long afterL1Id,
            String actor) {
        return Mono.defer(() -> {
            var rebuildLevel = parseLevel(level);
            if (conversationId == null || conversationId <= 0) {
                return Mono.error(invalid("重建记忆需要提供会话标识。"));
            }
            return conversationRepository
                    .findById(conversationId)
                    .switchIfEmpty(Mono.error(conversationNotFound()))
                    .flatMap(conversation -> switch (rebuildLevel) {
                        case L1 -> rebuildL1(conversation, actor);
                        case L2, L3 -> rebuildScope(
                                conversation, teamId, agentId, afterL1Id, actor, rebuildLevel);
                    });
        });
    }

    public Flux<String> export(
            @Nullable String teamId,
            @Nullable String agentId,
            @Nullable String userId,
            @Nullable MemoryType type,
            @Nullable MemoryStatus status,
            @Nullable Instant from,
            @Nullable Instant to,
            int limit) {
        return Flux.concat(
                Flux.just(MemoryCsvExporter.HEADER),
                repository
                        .exportL1(teamId, agentId, userId, type, status, from, to, limit)
                        .map(MemoryCsvExporter::line));
    }

    private Mono<RebuildResult> rebuildL1(Conversation conversation, String actor) {
        return messageRepository
                .findLatestId(conversation.id())
                .flatMap(lastMessageId -> lastMessageId <= 0
                        ? Mono.error(invalid("会话没有可处理的消息。"))
                        : memoryTaskScheduler.enqueueExtraction(conversation, actor, lastMessageId))
                .thenReturn(new RebuildResult(RebuildLevel.L1.name(), true));
    }

    private Mono<RebuildResult> rebuildScope(
            Conversation conversation,
            @Nullable String teamId,
            @Nullable String agentId,
            @Nullable Long afterL1Id,
            String actor,
            RebuildLevel level) {
        var resolvedTeamId = StringUtils.hasText(teamId) ? teamId.strip() : conversation.teamId();
        var resolvedAgentId = StringUtils.hasText(agentId) ? agentId.strip() : conversation.agentId();
        if (!StringUtils.hasText(resolvedTeamId) || !StringUtils.hasText(resolvedAgentId)) {
            return Mono.error(invalid("团队和 Agent 标识不能为空。"));
        }
        var cursor = afterL1Id == null ? 0L : Math.max(0L, afterL1Id);
        var enqueued = level == RebuildLevel.L2
                ? memoryTaskScheduler.enqueueL2(
                        resolvedTeamId, resolvedAgentId, conversation.id(), actor, cursor)
                : memoryTaskScheduler.enqueueL3(
                        resolvedTeamId, resolvedAgentId, conversation.id(), actor, cursor);
        return enqueued.thenReturn(new RebuildResult(level.name(), true));
    }

    private static RebuildLevel parseLevel(@Nullable String level) {
        if (!StringUtils.hasText(level)) {
            throw invalid("重建层级不能为空。");
        }
        try {
            return RebuildLevel.valueOf(level.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw invalid("重建层级不合法。");
        }
    }

    public static long parseId(String name) {
        var value = name == null ? "" : name.strip();
        var separator = value.indexOf(':');
        if (separator >= 0) {
            var prefix = value.substring(0, separator);
            if (!MEMORY_LEVEL_L1.equalsIgnoreCase(prefix)) {
                throw invalid("记忆标识格式不正确。");
            }
            value = value.substring(separator + 1);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            throw invalid("记忆标识格式不正确。");
        }
    }

    private Mono<L1MemoryRecord> transition(long id, MemoryStatus from, MemoryStatus to, String message) {
        return repository.findL1ById(id)
                .switchIfEmpty(Mono.error(notFound("记忆不存在。")))
                .flatMap(memory -> memory.status() == from
                        ? repository.updateL1Status(memory.id(), to)
                        : Mono.error(invalid(message)));
    }

    private Mono<L1MemoryRecord> deleteRecord(long memoryId, String reason, String actor) {
        return repository.findL1ById(memoryId)
                .switchIfEmpty(Mono.error(notFound("记忆不存在。")))
                .flatMap(memory -> {
                    if (memory.status() == MemoryStatus.DELETED) {
                        return Mono.error(new ApiException(
                                HttpStatus.FORBIDDEN,
                                CODE_DELETE_FORBIDDEN,
                                "记忆已删除，不能重复删除。"));
                    }
                    return memorySettings()
                            .flatMap(settings -> snapshotAndDelete(memory, reason, actor, settings));
                });
    }

    private Mono<L1MemoryRecord> snapshotAndDelete(
            L1MemoryRecord memory, String reason, String actor, AiSetting.Memory settings) {
        var payload = new SnapshotPayload(memory, actor, reason, Instant.now());
        var context = memory.context();
        return repository
                .insertSnapshot(
                        memory.id(),
                        context.userId(),
                        context.teamId(),
                        context.agentId(),
                        JsonUtils.objectToJson(payload),
                        settings.getDeleteRetentionDays())
                .then(repository.updateL1Status(memory.id(), MemoryStatus.DELETED));
    }

    private Mono<MemoryRequestRecord> submitRequest(
            String userId,
            L1MemoryRecord memory,
            MemoryRequestType requestType,
            String reason,
            String suggestedContent) {
        var request = new NewMemoryRequest(
                userId,
                memory.context().teamId(),
                memory.context().agentId(),
                MEMORY_LEVEL_L1,
                memory.id(),
                requestType.name(),
                reason,
                suggestedContent);
        return repository
                .insertRequest(request)
                .onErrorMap(
                        DuplicateKeyException.class,
                        error -> conflict("该记忆已有待处理的同类申请。"))
                .flatMap(created -> memory.status() == MemoryStatus.ACTIVE
                        ? repository
                                .updateL1Status(memory.id(), MemoryStatus.PENDING_CORRECTION)
                                .thenReturn(created)
                        : Mono.just(created));
    }

    private Mono<MemoryRequestRecord> findPendingRequest(long requestId) {
        return repository
                .findRequestById(requestId)
                .switchIfEmpty(Mono.error(notFound("记忆申请不存在。")))
                .flatMap(request -> MemoryRequestStatus.PENDING.name().equals(request.status())
                        ? Mono.just(request)
                        : Mono.error(invalid("申请已处理。")));
    }

    private Mono<MemoryRequestRecord> completeRequest(
            long requestId, MemoryRequestStatus status, String reviewer, @Nullable String comment) {
        return repository
                .updateRequestStatus(
                        requestId, status.name(), reviewer, StringUtils.hasText(comment) ? comment.strip() : null)
                .switchIfEmpty(Mono.error(invalid("申请已处理。")));
    }

    private Mono<L1MemoryRecord> correctRecord(long memoryId, @Nullable String suggestedContent) {
        if (!StringUtils.hasText(suggestedContent)) {
            return Mono.error(invalid("更正内容不能为空。"));
        }
        return repository
                .updateL1ContentAndStatus(memoryId, suggestedContent, null, MemoryStatus.ACTIVE)
                .switchIfEmpty(Mono.error(notFound("记忆不存在。")));
    }

    private Mono<Void> releaseCorrection(long memoryId) {
        return repository
                .findL1ById(memoryId)
                .filter(memory -> memory.status() == MemoryStatus.PENDING_CORRECTION)
                .flatMap(memory -> repository.updateL1Status(memory.id(), MemoryStatus.ACTIVE))
                .then();
    }

    private Mono<AiSetting.Memory> memorySettings() {
        return aiSettings
                .spec()
                .map(spec -> spec.getMemory() == null ? new AiSetting.Memory() : spec.getMemory());
    }

    private static PipelineStats stats(List<TaskStatusCount> counts, long pendingConversations) {
        var totals = new long[4];
        var byType = new LinkedHashMap<String, long[]>();
        for (var count : counts) {
            var position = position(count.status());
            if (position < 0) {
                continue;
            }
            totals[position] += count.count();
            var values = byType.computeIfAbsent(count.taskType(), key -> new long[4]);
            values[position] += count.count();
        }
        var taskTypes = byType.entrySet().stream()
                .map(entry -> new PipelineTaskType(
                        entry.getKey(),
                        entry.getValue()[0],
                        entry.getValue()[1],
                        entry.getValue()[2],
                        entry.getValue()[3]))
                .toList();
        return new PipelineStats(
                totals[0], totals[1], totals[2], totals[3], pendingConversations, taskTypes);
    }

    private static int position(int status) {
        return switch (status) {
            case TASK_PENDING -> 0;
            case TASK_RUNNING -> 1;
            case TASK_DONE -> 2;
            case TASK_FAILED -> 3;
            default -> -1;
        };
    }

    private static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, CODE_NOT_FOUND, message);
    }

    private static ApiException conversationNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, CODE_CONVERSATION_NOT_FOUND, "会话不存在。");
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, CODE_REQUEST_INVALID, message);
    }

    private static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, CODE_REQUEST_CONFLICT, message);
    }

    private enum RebuildLevel {
        L1,
        L2,
        L3
    }

    public enum MemoryRequestType {
        DELETE,
        CORRECT
    }

    public enum MemoryRequestStatus {
        PENDING,
        APPROVED,
        REJECTED
    }

    public record SnapshotPayload(
            L1MemoryRecord record, String actor, @Nullable String reason, Instant createdAt) {}

    public record RebuildResult(String level, boolean queued) {}

    public record PipelineTaskType(String taskType, long pending, long running, long done, long failed) {}

    public record PipelineStats(
            long pendingTasks,
            long runningTasks,
            long doneTasks,
            long failedTasks,
            long pendingConversations,
            List<PipelineTaskType> taskTypes) {}
}
