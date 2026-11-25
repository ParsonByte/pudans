package cn.tolim.pudans.core.agent.runtime;

import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.core.agent.memory.AgentMemoryContext;
import cn.tolim.pudans.core.agent.tool.AgentToolContext;
import cn.tolim.pudans.core.agent.tool.GenerateMediaTool;
import cn.tolim.pudans.core.ai.quota.AiPricing;
import cn.tolim.pudans.core.ai.quota.AiQuotas;
import cn.tolim.pudans.core.ai.usage.AiUsage;
import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.agent.AgentApprovalEntity;
import cn.tolim.pudans.infra.r2dbc.agent.AgentApprovalRepository;
import cn.tolim.pudans.infra.r2dbc.agent.AgentMessageEntity;
import cn.tolim.pudans.infra.r2dbc.agent.AgentMessageRepository;
import cn.tolim.pudans.infra.r2dbc.agent.AgentRunEntity;
import cn.tolim.pudans.infra.r2dbc.agent.AgentRunRepository;
import cn.tolim.pudans.infra.r2dbc.agent.AgentToolCallEntity;
import cn.tolim.pudans.infra.r2dbc.agent.AgentToolCallRepository;
import cn.tolim.pudans.infra.utils.Digests;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.ExceedMaxItersEvent;
import io.agentscope.core.event.ModelCallStartEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import io.agentscope.core.tool.Toolkit;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentScopeRuntime implements AgentRuntime {

    private static final String RUNNING = "running";

    private static final String WAITING_APPROVAL = "waiting_approval";

    private static final String COMPLETED = "completed";

    private static final String FAILED = "failed";

    private static final String CANCELLED = "cancelled";

    private static final Duration APPROVAL_TTL = Duration.ofMinutes(5);

    private static final int DEFAULT_OUTPUT_RESERVE_TOKENS = 1024;

    private static final String SYSTEM_PROMPT =
            "你是 Pudans 工作区助手。调用 generate_media 时必须使用真实模型并返回真实任务标识，不得伪造生成结果。";

    private final AgentModelRouteResolver routes;

    private final GenerateMediaTool mediaTool;

    private final AgentRunEvents events;

    private final AgentRunRepository runs;

    private final AgentMessageRepository messages;

    private final AgentToolCallRepository toolCalls;

    private final AgentApprovalRepository approvals;

    private final AiQuotas quotas;

    private final AiPricing pricing;

    private final AgentChatModelFactory modelFactory;

    private final AgentMemoryContext memoryContext;

    private final Map<UUID, ActiveExecution> activeRuns = new ConcurrentHashMap<>();

    private Clock clock = Clock.systemUTC();

    @Override
    public Mono<Void> start(AgentRunContext context) {
        return Mono.defer(() -> {
            if (activeRuns.containsKey(context.runId())) {
                return Mono.error(alreadyActive());
            }
            return prepare(context).flatMap(active -> {
                if (activeRuns.putIfAbsent(context.runId(), active) != null) {
                    closeAgent(active);
                    return releaseQuota(active).then(Mono.error(alreadyActive()));
                }
                launch(active, new UserMessage(context.prompt()));
                return Mono.empty();
            });
        });
    }

    @Override
    public Mono<Boolean> cancel(AgentRunContext context) {
        return Mono.defer(() -> {
            var active = activeRuns.get(context.runId());
            if (active == null) {
                return Mono.just(false);
            }
            active.cancelled.set(true);
            try {
                active.agent.interrupt(active.runtimeContext);
            } catch (RuntimeException error) {
                log.warn("中断 AgentScope 运行失败：run={}", context.runId(), error);
            }
            return finish(
                            active,
                            AgentRunEvents.RUN_CANCELLED,
                            CANCELLED,
                            null,
                            null,
                            Map.of("reason", "cancel-requested"))
                    .thenReturn(true);
        });
    }

    @Override
    public Mono<Boolean> resume(AgentRunContext context, UUID approvalId, boolean approved) {
        return Mono.defer(() -> {
            var active = activeRuns.get(context.runId());
            if (active == null || active.terminal.get() || active.cancelled.get()) {
                return Mono.just(false);
            }
            var toolCall = active.pendingApprovals.get(approvalId);
            if (toolCall == null) {
                return Mono.just(false);
            }
            return markRunning(active).doOnNext(ignored -> {
                active.pendingApprovals.remove(approvalId);
                active.waitingApproval.set(false);
                launch(active, confirmation(toolCall, approved));
            }).thenReturn(true);
        });
    }

    private Mono<ActiveExecution> prepare(AgentRunContext context) {
        return routes.resolve(context.workspaceId(), context.modelId()).flatMap(route -> memoryContext
                .recall(context.ownerId(), context.workspaceId(), context.prompt())
                .flatMap(remembered -> {
                    var agent = createAgent(route, context, remembered);
                    return reserve(context, route.model())
                            .flatMap(reservation -> markStarted(context, reservation.id())
                                    .onErrorResume(error -> releaseReservation(reservation.id())
                                            .then(Mono.error(error)))
                                    .thenReturn(active(context, route, agent, reservation)))
                            .onErrorResume(error -> {
                                close(agent);
                                return Mono.error(error);
                            });
                }));
    }

    // 使用真实 AgentScope ReActAgent，并注册真实的生成工具。
    ReActAgent createAgent(
            AgentModelRoute route, AgentRunContext context, String rememberedContext) {
        var toolkit = new Toolkit();
        toolkit.registerTool(mediaTool);
        var permissions = PermissionContextState.builder()
                .mode(PermissionMode.DEFAULT)
                .addAskRule(
                        GenerateMediaTool.NAME,
                        new PermissionRule(
                                GenerateMediaTool.NAME,
                                null,
                                PermissionBehavior.ASK,
                                "pudans-agent"))
                .build();
        return ReActAgent.builder()
                .name("pudans-agent")
                .description("Pudans 工作区对话助手")
                .sysPrompt(systemPrompt(rememberedContext))
                .model(modelFactory.create(route, context.reasoning()))
                .toolkit(toolkit)
                .permissionContext(permissions)
                .stopOnReject(true)
                .build();
    }

    private Mono<ReservedQuota> reserve(AgentRunContext context, AiModel model) {
        var inputTokens = BigDecimal.valueOf(
                Math.max(1L, (context.prompt().length() + 3L) / 4L));
        var outputTokens = BigDecimal.valueOf(model.maxOutputTokens() == null
                ? DEFAULT_OUTPUT_RESERVE_TOKENS
                : Math.max(1, model.maxOutputTokens()));
        var usage = new AiPricing.TokenUsage(inputTokens, BigDecimal.ZERO, outputTokens);
        return pricing
                .quoteTokens(model.id(), usage, now().toInstant())
                .flatMap(quote -> quotas.reserve(
                                new RequestIdentity(context.ownerId(), context.workspaceId()),
                                context.roles(),
                                new AiQuotas.ReservationRequest(
                                        context.runId(),
                                        null,
                                        quote.credits(),
                                        1,
                                        0,
                                        "agent-run:" + context.runId()))
                        .map(reservation -> new ReservedQuota(
                                reservation.id(),
                                reservation.reservedCredits() == null
                                        ? quote.credits()
                                        : reservation.reservedCredits())));
    }

    private Mono<Void> markStarted(AgentRunContext context, UUID quotaReservationId) {
        return requireRun(context.runId()).flatMap(run -> {
            if (run.isCancelRequested() || CANCELLED.equals(run.getStatus())) {
                return Mono.error(new ApiException(
                        HttpStatus.CONFLICT,
                        "agent-run-cancelled",
                        "Agent 运行已经取消。"));
            }
            if (terminal(run.getStatus())) {
                return Mono.error(new ApiException(
                        HttpStatus.CONFLICT,
                        "agent-run-terminal",
                        "Agent 运行已经结束。"));
            }
            run.setStatus(RUNNING);
            run.setStartedAt(now());
            run.setQuotaReservationId(quotaReservationId);
            return runs.save(run).then();
        });
    }

    private ActiveExecution active(
            AgentRunContext context,
            AgentModelRoute route,
            ReActAgent agent,
            ReservedQuota reservation) {
        var toolContext = new AgentToolContext(
                context.runId(),
                context.sessionId(),
                context.ownerId(),
                context.workspaceId(),
                reservation.id());
        var runtimeContext = RuntimeContext.builder()
                .sessionId(context.sessionId().toString())
                .userId(context.ownerId())
                .put(AgentToolContext.class, toolContext)
                .put("runId", context.runId().toString())
                .put("workspaceId", context.workspaceId())
                .build();
        return new ActiveExecution(
                context,
                route,
                agent,
                runtimeContext,
                reservation.id(),
                reservation.credits());
    }

    private void launch(ActiveExecution active, Msg input) {
        try {
            execute(active, input)
                    .doFinally(ignored -> {
                        if (active.terminal.get() || active.cancelled.get()) {
                            closeAgent(active);
                        }
                    })
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe(ignored -> {}, error -> {
                        log.error(
                                "AgentScope 运行以未处理错误结束：run={}",
                                active.context.runId(),
                                error);
                        fail(active, error).subscribe();
                    });
        } catch (RuntimeException error) {
            log.error("启动 AgentScope 运行失败：run={}", active.context.runId(), error);
            fail(active, error).subscribe();
        }
    }

    private Mono<Void> execute(ActiveExecution active, Msg input) {
        return active.agent
                .streamEvents(List.of(input), active.runtimeContext)
                .concatMap(event -> handle(active, event))
                .then(Mono.defer(() -> ensureSettled(active)))
                .onErrorResume(error -> failOrCancel(active, error));
    }

    private Mono<Void> handle(ActiveExecution active, AgentEvent event) {
        if (event instanceof ModelCallStartEvent) {
            return markProviderRequestSent(active);
        }
        if (event instanceof ToolCallStartEvent tool) {
            return ensureToolCall(active, tool.getToolCallId(), tool.getToolCallName())
                    .then(publish(active, AgentScopeEventMapper.map(event)));
        }
        if (event instanceof ToolResultEndEvent tool) {
            return finishToolCall(active, tool).then(publish(
                    active, AgentScopeEventMapper.map(event)));
        }
        if (event instanceof RequireUserConfirmEvent confirmation) {
            return handleConfirmation(active, confirmation);
        }
        if (event instanceof AgentResultEvent result) {
            return handleResult(active, result);
        }
        if (event instanceof ExceedMaxItersEvent) {
            var mapped = AgentScopeEventMapper.map(event);
            return finish(
                    active,
                    AgentRunEvents.RUN_FAILED,
                    FAILED,
                    "agent-max-iterations",
                    "Agent 达到最大推理轮次。",
                    mapped.data());
        }
        return publish(active, AgentScopeEventMapper.map(event));
    }

    private Mono<Void> handleResult(ActiveExecution active, AgentResultEvent event) {
        var result = event.getResult();
        var mapped = AgentScopeEventMapper.map(event);
        return persistAssistant(active, result).flatMap(messageId -> {
            if (mapped == null || mapped.type() == null) {
                return Mono.empty();
            }
            active.finalResult.compareAndSet(null, result);
            var data = new LinkedHashMap<>(mapped.data());
            data.put("messageId", messageId);
            var failure = failure(result);
            recordMemory(active, result);
            return finish(
                    active,
                    mapped.type(),
                    AgentScopeEventMapper.statusForType(mapped.type()),
                    failure.code(),
                    failure.message(),
                    data);
        });
    }

    private Mono<UUID> persistAssistant(ActiveExecution active, Msg result) {
        var entity = AgentMessageEntity.assistantMessage(
                active.context.sessionId(),
                active.context.runId(),
                AgentScopeEventMapper.assistantContent(result),
                AgentScopeEventMapper.toolCalls(result),
                AgentScopeEventMapper.usage(result),
                now());
        return messages.save(entity).flatMap(saved -> requireRun(active.context.runId())
                .flatMap(run -> {
                    run.setOutputMessageId(saved.getId());
                    return runs.save(run);
                })
                .thenReturn(saved.getId()));
    }

    private Mono<Void> handleConfirmation(
            ActiveExecution active, RequireUserConfirmEvent event) {
        var calls = event.getToolCalls() == null ? List.<ToolUseBlock>of() : event.getToolCalls();
        if (calls.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(calls)
                .concatMap(toolCall -> ensureToolCall(
                                active, toolCall.getId(), toolCall.getName())
                        .flatMap(toolCallId -> persistApproval(active, toolCall, toolCallId)))
                .collectList()
                .flatMap(pending -> {
                    pending.forEach(value ->
                            active.pendingApprovals.put(value.approvalId(), value.toolCall()));
                    active.waitingApproval.set(true);
                    return markWaiting(active).thenMany(Flux.fromIterable(pending)
                            .concatMap(value -> events.publish(
                                    active.context.runId(),
                                    active.context.sessionId(),
                                    AgentRunEvents.TOOL_APPROVAL_REQUIRED,
                                    approvalData(value))))
                            .then();
                });
    }

    private static String systemPrompt(String rememberedContext) {
        return StringUtils.hasText(rememberedContext)
                ? SYSTEM_PROMPT + "\n\n" + rememberedContext
                : SYSTEM_PROMPT;
    }

    private void recordMemory(ActiveExecution active, Msg result) {
        var assistantText = result == null ? null : result.getTextContent();
        memoryContext
                .record(
                        active.context.ownerId(),
                        active.context.workspaceId(),
                        active.context.prompt(),
                        assistantText)
                .subscribe(
                        ignored -> {},
                        error -> log.warn(
                                "写入 Agent 长期记忆失败：run={}", active.context.runId(), error));
    }

    private Mono<PendingApproval> persistApproval(
            ActiveExecution active, ToolUseBlock toolCall, UUID toolCallId) {
        return approvals
                .findFirstByRunIdAndToolCallIdAndStatus(
                        active.context.runId(), toolCallId, "pending")
                .map(existing -> new PendingApproval(
                        existing.getId(), existing.getExpiresAt(), toolCall))
                .switchIfEmpty(Mono.defer(() -> {
                    var approval = new AgentApprovalEntity();
                    approval.setId(UUID.randomUUID());
                    approval.setRunId(active.context.runId());
                    approval.setToolCallId(toolCallId);
                    approval.setStatus("pending");
                    var request = approvalRequest(active, toolCall);
                    approval.setRequestJson(JsonUtils.objectToJson(request));
                    approval.setExpiresAt(now().plus(APPROVAL_TTL));
                    return approvals
                            .save(approval)
                            .map(saved -> new PendingApproval(
                                    saved.getId(), saved.getExpiresAt(), toolCall));
                }));
    }

    private Mono<Void> markWaiting(ActiveExecution active) {
        return requireRun(active.context.runId()).flatMap(run -> {
            run.setStatus(WAITING_APPROVAL);
            return runs.save(run).then();
        });
    }

    private Mono<Void> markRunning(ActiveExecution active) {
        return requireRun(active.context.runId()).flatMap(run -> {
            if (terminal(run.getStatus())) {
                return Mono.error(new ApiException(
                        HttpStatus.CONFLICT,
                        "agent-run-terminal",
                        "运行已经结束，不能恢复。"));
            }
            run.setStatus(RUNNING);
            run.setErrorCode(null);
            run.setErrorMessage(null);
            return runs.save(run).then();
        });
    }

    private Mono<Void> markProviderRequestSent(ActiveExecution active) {
        if (!active.providerRequestSent.compareAndSet(false, true)) {
            return Mono.empty();
        }
        return requireRun(active.context.runId())
                .flatMap(run -> {
                    run.setProviderRequestSent(true);
                    return runs.save(run);
                })
                .then();
    }

    private Mono<UUID> ensureToolCall(ActiveExecution active, String toolCallId, String toolName) {
        var resolvedId = StringUtils.hasText(toolCallId) ? toolCallId.trim() : UUID.randomUUID().toString();
        var existing = active.toolCallIds.get(resolvedId);
        if (existing != null) {
            return Mono.just(existing);
        }
        var auditId = UUID.randomUUID();
        var previous = active.toolCallIds.putIfAbsent(resolvedId, auditId);
        if (previous != null) {
            return Mono.just(previous);
        }
        var entity = new AgentToolCallEntity();
        entity.setId(auditId);
        entity.setRunId(active.context.runId());
        entity.setSessionId(active.context.sessionId());
        entity.setOwnerId(active.context.ownerId());
        entity.setWorkspaceId(active.context.workspaceId());
        entity.setToolName(StringUtils.hasText(toolName) ? toolName : "unknown");
        entity.setMcp(false);
        entity.setStatus("started");
        entity.setIdempotencyKey(
                Digests.sha256Hex(active.context.runId() + "\0" + resolvedId));
        entity.setStartedAt(now());
        return toolCalls
                .save(entity)
                .thenReturn(auditId)
                .onErrorResume(error -> {
                    active.toolCallIds.remove(resolvedId, auditId);
                    return Mono.error(error);
                });
    }

    private Mono<Void> finishToolCall(ActiveExecution active, ToolResultEndEvent event) {
        var auditId = event.getToolCallId() == null
                ? null
                : active.toolCallIds.get(event.getToolCallId());
        if (auditId == null) {
            return Mono.empty();
        }
        return toolCalls.findById(auditId).flatMap(entity -> {
            entity.setStatus(toolState(event.getState()));
            entity.setFinishedAt(now());
            return toolCalls.save(entity);
        }).then();
    }

    private Mono<Void> finish(
            ActiveExecution active,
            String type,
            String status,
            String errorCode,
            String errorMessage,
            Map<String, Object> data) {
        if (!active.terminal.compareAndSet(false, true)) {
            return Mono.empty();
        }
        var eventData = new LinkedHashMap<String, Object>();
        if (data != null) {
            eventData.putAll(data);
        }
        eventData.put("status", status);
        if (errorCode != null) {
            eventData.put("errorCode", errorCode);
        }
        if (errorMessage != null) {
            eventData.put("errorMessage", errorMessage);
        }
        return settleQuota(active)
                .then(requireRun(active.context.runId()))
                .flatMap(run -> {
                    run.setStatus(status);
                    run.setErrorCode(errorCode);
                    run.setErrorMessage(errorMessage);
                    run.setFinishedAt(now());
                    return runs.save(run);
                })
                .then(events.publish(
                        active.context.runId(), active.context.sessionId(), type, eventData))
                .doFinally(ignored -> {
                    activeRuns.remove(active.context.runId(), active);
                    closeAgent(active);
                });
    }

    private Mono<Void> settleIncomplete() {
        return Mono.error(new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "agent-stream-incomplete",
                "AgentScope 事件流在终态前结束。"));
    }

    private Mono<Void> ensureSettled(ActiveExecution active) {
        if (active.terminal.get() || active.waitingApproval.get()) {
            return Mono.empty();
        }
        return settleIncomplete();
    }

    private Mono<Void> failOrCancel(ActiveExecution active, Throwable error) {
        if (active.terminal.get()) {
            return Mono.empty();
        }
        if (active.cancelled.get() || interrupted(error)) {
            return finish(
                    active,
                    AgentRunEvents.RUN_CANCELLED,
                    CANCELLED,
                    null,
                    null,
                    Map.of("reason", "interrupted"));
        }
        return fail(active, error);
    }

    private Mono<Void> fail(ActiveExecution active, Throwable error) {
        var code = error instanceof ApiException api ? api.getCode() : "agent-runtime-failed";
        var message = StringUtils.hasText(error.getMessage()) ? error.getMessage() : "Agent 运行时失败。";
        return finish(
                active,
                AgentRunEvents.RUN_FAILED,
                FAILED,
                code,
                message,
                Map.of());
    }

    private Mono<Void> settleQuota(ActiveExecution active) {
        if (active.quotaReservationId == null) {
            return Mono.empty();
        }
        if (!active.providerRequestSent.get()) {
            return releaseReservation(active.quotaReservationId);
        }
        return settlementCredits(active)
                .flatMap(credits -> quotas.settle(
                        active.quotaReservationId,
                        credits,
                        active.toolCallIds.size(),
                        0))
                .then()
                .onErrorResume(error -> {
                    log.warn(
                            "结算 Agent 配额失败：run={} reservation={}",
                            active.context.runId(),
                            active.quotaReservationId,
                            error);
                    return Mono.empty();
                });
    }

    private Mono<BigDecimal> settlementCredits(ActiveExecution active) {
        var result = active.finalResult.get();
        var chatUsage = result == null ? null : result.getChatUsage();
        if (chatUsage == null) {
            return Mono.just(active.reservedCredits);
        }
        var usage = AiUsage.of(
                chatUsage.getInputTokens(),
                chatUsage.getOutputTokens(),
                chatUsage.getCachedTokens(),
                chatUsage.getTotalTokens());
        if (!usage.usable()) {
            return Mono.just(active.reservedCredits);
        }
        var tokenUsage = new AiPricing.TokenUsage(
                BigDecimal.valueOf(usage.inputTokens()),
                BigDecimal.valueOf(usage.cachedTokens()),
                BigDecimal.valueOf(usage.outputTokens()));
        return pricing
                .quoteTokens(active.route.model().id(), tokenUsage, now().toInstant())
                .map(AiPricing.PriceQuote::credits)
                .onErrorResume(error -> {
                    log.warn(
                            "按实际用量计价失败，改用预扣额度：run={}",
                            active.context.runId(),
                            error);
                    return Mono.just(active.reservedCredits);
                });
    }

    private Mono<Void> releaseQuota(ActiveExecution active) {
        return releaseReservation(active.quotaReservationId);
    }

    private Mono<Void> releaseReservation(UUID reservationId) {
        if (reservationId == null) {
            return Mono.empty();
        }
        return quotas
                .release(reservationId)
                .then()
                .onErrorResume(error -> {
                    log.warn("释放 Agent 配额预扣失败：reservation={}", reservationId, error);
                    return Mono.empty();
                });
    }

    private Mono<Void> publish(ActiveExecution active, AgentScopeEventMapper.MappedEvent mapped) {
        if (mapped == null || mapped.type() == null) {
            return Mono.empty();
        }
        return events.publish(
                active.context.runId(),
                active.context.sessionId(),
                mapped.type(),
                mapped.data());
    }

    private Mono<AgentRunEntity> requireRun(UUID runId) {
        return runs
                .findById(runId)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, "agent-run-not-found", "Agent 运行不存在。")));
    }

    private static ApiException alreadyActive() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "agent-run-already-active",
                "该 Agent 运行已经启动。");
    }

    private UserMessage confirmation(ToolUseBlock toolCall, boolean approved) {
        return UserMessage.builder()
                .metadata(Map.of(
                        Msg.METADATA_CONFIRM_RESULTS,
                        List.of(new ConfirmResult(approved, toolCall))))
                .build();
    }

    private Map<String, Object> approvalRequest(ActiveExecution active, ToolUseBlock toolCall) {
        var request = new LinkedHashMap<String, Object>();
        request.put("runId", active.context.runId());
        request.put("sessionId", active.context.sessionId());
        request.put("toolCall", AgentScopeEventMapper.toolCall(toolCall));
        return request;
    }

    private static Map<String, Object> approvalData(PendingApproval pending) {
        var data = new LinkedHashMap<String, Object>();
        data.put("approvalId", pending.approvalId());
        data.put("status", "pending");
        data.put("expiresAt", pending.expiresAt().toInstant());
        data.put("toolCall", AgentScopeEventMapper.toolCall(pending.toolCall()));
        return data;
    }

    private static Failure failure(Msg result) {
        if (result == null || result.getGenerateReason() == null) {
            return new Failure(null, null);
        }
        return switch (result.getGenerateReason()) {
            case MAX_ITERATIONS -> new Failure("agent-max-iterations", "Agent 达到最大推理轮次。");
            case ALL_TOOLS_DENIED -> new Failure("agent-tools-denied", "所有工具调用均被拒绝。");
            default -> new Failure(null, null);
        };
    }

    private static String toolState(ToolResultState state) {
        if (state == null) {
            return "finished";
        }
        return switch (state) {
            case SUCCESS -> "succeeded";
            case ERROR -> "failed";
            case DENIED -> "denied";
            case INTERRUPTED -> "cancelled";
            case RUNNING -> "running";
        };
    }

    private static boolean interrupted(Throwable error) {
        for (var current = error; current != null; current = current.getCause()) {
            if (current instanceof InterruptedException) {
                return true;
            }
        }
        return false;
    }

    private static boolean terminal(String status) {
        return COMPLETED.equals(status)
                || FAILED.equals(status)
                || CANCELLED.equals(status);
    }

    private static void closeAgent(ActiveExecution active) {
        if (active.closed.compareAndSet(false, true)) {
            close(active.agent);
        }
    }

    private static void close(ReActAgent agent) {
        if (agent == null) {
            return;
        }
        try {
            agent.close();
        } catch (RuntimeException error) {
            log.warn("关闭 AgentScope 运行失败", error);
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    private record ReservedQuota(UUID id, BigDecimal credits) {}

    private record PendingApproval(
            UUID approvalId, OffsetDateTime expiresAt, ToolUseBlock toolCall) {}

    private record Failure(String code, String message) {}

    private static final class ActiveExecution {

        private final AgentRunContext context;

        private final AgentModelRoute route;

        private final ReActAgent agent;

        private final RuntimeContext runtimeContext;

        private final UUID quotaReservationId;

        private final BigDecimal reservedCredits;

        private final AtomicBoolean terminal = new AtomicBoolean();

        private final AtomicBoolean providerRequestSent = new AtomicBoolean();

        private final AtomicBoolean cancelled = new AtomicBoolean();

        private final AtomicBoolean waitingApproval = new AtomicBoolean();

        private final AtomicBoolean closed = new AtomicBoolean();

        private final Map<String, UUID> toolCallIds = new ConcurrentHashMap<>();

        private final Map<UUID, ToolUseBlock> pendingApprovals = new ConcurrentHashMap<>();

        private final AtomicReference<Msg> finalResult = new AtomicReference<>();

        private ActiveExecution(
                AgentRunContext context,
                AgentModelRoute route,
                ReActAgent agent,
                RuntimeContext runtimeContext,
                UUID quotaReservationId,
                BigDecimal reservedCredits) {
            this.context = context;
            this.route = route;
            this.agent = agent;
            this.runtimeContext = runtimeContext;
            this.quotaReservationId = quotaReservationId;
            this.reservedCredits = reservedCredits;
        }
    }
}
