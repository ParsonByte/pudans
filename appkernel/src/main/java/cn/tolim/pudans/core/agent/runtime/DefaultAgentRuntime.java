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
import com.fasterxml.jackson.core.type.TypeReference;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.anthropic.AnthropicTokenUsage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.adapter.JdkFlowAdapter;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultAgentRuntime implements AgentRuntime {

    private static final String RUNNING = "running";

    private static final String WAITING_APPROVAL = "waiting_approval";

    private static final String COMPLETED = "completed";

    private static final String FAILED = "failed";

    private static final String CANCELLED = "cancelled";

    private static final String AGENT_NAME = "pudans-agent";

    private static final String AGENT_ROLE = "assistant";

    private static final Duration APPROVAL_TTL = Duration.ofMinutes(5);

    private static final int MAX_ITERATIONS = 10;

    private static final int DEFAULT_OUTPUT_RESERVE_TOKENS = 1024;

    private static final int DEFAULT_STREAM_TIMEOUT_MS = 60_000;

    private static final String SYSTEM_PROMPT =
            "你是 Pudans 工作区助手。调用 generate_media 时必须使用真实模型并返回真实任务标识，不得伪造生成结果。";

    private static final TypeReference<Map<String, Object>> OBJECT_TYPE = new TypeReference<>() {};

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
                    return releaseQuota(active).then(Mono.error(alreadyActive()));
                }
                launch(active);
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
            dispose(active);
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
            var pending = active.pendingApprovals.remove(approvalId);
            if (pending == null) {
                return Mono.just(false);
            }
            active.approvalsInFlight.incrementAndGet();
            if (!approved) {
                active.roundDenied.incrementAndGet();
                return settleApproval(active, deny(active, pending)).thenReturn(true);
            }
            return settleApproval(active, executeApproved(active, pending)).thenReturn(true);
        });
    }

    private Mono<Void> settleApproval(ActiveExecution active, Mono<Void> action) {
        var decremented = new AtomicBoolean();
        return action
                .then(Mono.defer(() -> {
                    if (decremented.compareAndSet(false, true)) {
                        active.approvalsInFlight.decrementAndGet();
                    }
                    return continueAfterApprovals(active);
                }))
                .onErrorResume(error -> {
                    if (decremented.compareAndSet(false, true)) {
                        active.approvalsInFlight.decrementAndGet();
                    }
                    return fail(active, error);
                });
    }

    private Mono<Void> continueAfterApprovals(ActiveExecution active) {
        synchronized (active.approvalLock) {
            if (!active.pendingApprovals.isEmpty() || active.approvalsInFlight.get() > 0) {
                return Mono.empty();
            }
            if (active.terminal.get() || active.cancelled.get()) {
                return Mono.empty();
            }
            if (active.roundToolCalls.get() > 0
                    && active.roundDenied.get() >= active.roundToolCalls.get()) {
                return finish(
                        active,
                        AgentRunEvents.RUN_FAILED,
                        FAILED,
                        "agent-tools-denied",
                        "所有工具调用均被拒绝。",
                        Map.of());
            }
            active.launchPending.set(true);
            active.waitingApproval.set(false);
            return markRunning(active).then(Mono.fromRunnable(() -> launch(active)));
        }
    }

    private Mono<ActiveExecution> prepare(AgentRunContext context) {
        return routes.resolve(context.workspaceId(), context.modelId()).flatMap(route -> memoryContext
                .recall(context.ownerId(), context.workspaceId(), context.prompt())
                .flatMap(remembered -> {
                    var model = modelFactory.create(route, context.reasoning());
                    return reserve(context, route.model())
                            .flatMap(reservation -> markStarted(context, reservation.id())
                                    .onErrorResume(error -> releaseReservation(reservation.id())
                                            .then(Mono.error(error)))
                                    .thenReturn(active(
                                            context, route, remembered, reservation, model)));
                }));
    }

    private ActiveExecution active(
            AgentRunContext context,
            AgentModelRoute route,
            String rememberedContext,
            ReservedQuota reservation,
            StreamingChatModel model) {
        var toolContext = new AgentToolContext(
                context.runId(),
                context.sessionId(),
                context.ownerId(),
                context.workspaceId(),
                reservation.id());
        var active = new ActiveExecution(
                context, route, model, toolContext, reservation.id(), reservation.credits());
        active.messages.add(
                dev.langchain4j.data.message.SystemMessage.from(systemPrompt(rememberedContext)));
        active.messages.add(UserMessage.from(context.prompt()));
        return active;
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

    private void launch(ActiveExecution active) {
        if (!active.looping.compareAndSet(false, true)) {
            active.launchPending.set(true);
            return;
        }
        active.launchPending.set(false);
        try {
            var subscription = execute(active)
                    .doFinally(ignored -> {
                        active.looping.set(false);
                        if (active.launchPending.getAndSet(false)
                                && !active.terminal.get()
                                && !active.cancelled.get()) {
                            launch(active);
                        }
                    })
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe(ignored -> {}, error -> {
                        log.error(
                                "Agent 运行以未处理错误结束：run={}",
                                active.context.runId(),
                                error);
                        fail(active, error).subscribe();
                    });
            active.subscription.update(subscription);
        } catch (RuntimeException error) {
            active.looping.set(false);
            log.error("启动 Agent 运行失败：run={}", active.context.runId(), error);
            fail(active, error).subscribe();
        }
    }

    // 每次模型调用都是一轮 ReAct 推理；工具审批会暂停在等待态。
    private Mono<Void> execute(ActiveExecution active) {
        return Mono.defer(() -> {
            if (active.cancelled.get() || active.terminal.get()) {
                return Mono.empty();
            }
            if (active.iterations.incrementAndGet() > MAX_ITERATIONS) {
                return finish(
                        active,
                        AgentRunEvents.RUN_FAILED,
                        FAILED,
                        "agent-max-iterations",
                        "Agent 达到最大推理轮次。",
                        Map.of("maxIters", MAX_ITERATIONS));
            }
            var reply = new ReplyState();
            return events.publish(
                            active.context.runId(),
                            active.context.sessionId(),
                            AgentRunEvents.RUN_STARTED,
                            startData(active, reply))
                    .then(markProviderRequestSent(active))
                    .then(stream(active, reply))
                    .then(Mono.defer(() -> ensureSettled(active)));
        }).onErrorResume(error -> failOrCancel(active, error));
    }

    private Mono<Void> stream(ActiveExecution active, ReplyState reply) {
        var request = ChatRequest.builder()
                .messages(List.copyOf(active.messages))
                .toolSpecifications(mediaTool.specification())
                .build();
        var timeoutMs = active.route.channel().timeoutMs();
        var effectiveTimeout = timeoutMs == null || timeoutMs <= 0
                ? DEFAULT_STREAM_TIMEOUT_MS
                : timeoutMs;
        return JdkFlowAdapter.flowPublisherToFlux(active.model.chat(request))
                .timeout(Duration.ofMillis(effectiveTimeout))
                .onErrorMap(
                        TimeoutException.class,
                        error -> new ApiException(
                                HttpStatus.GATEWAY_TIMEOUT,
                                "agent-stream-timeout",
                                "模型事件流超时。",
                                error))
                .concatMap(event -> handle(active, reply, event))
                .then();
    }

    private Mono<Void> handle(
            ActiveExecution active, ReplyState reply, ChatModelStreamingEvent event) {
        if (event instanceof PartialResponse partial) {
            return publishDelta(active, reply, partial.text());
        }
        if (event instanceof PartialThinking partial) {
            return publishReasoning(active, reply, partial.text());
        }
        if (event instanceof CompleteResponse complete) {
            return complete(active, reply, complete.chatResponse());
        }
        return Mono.empty();
    }

    private Mono<Void> publishDelta(ActiveExecution active, ReplyState reply, String delta) {
        if (!StringUtils.hasText(delta)
                || active.cancelled.get()
                || active.terminal.get()) {
            return Mono.empty();
        }
        var start = reply.textStarted
                ? Mono.<Void>empty()
                : events.publish(
                        active.context.runId(),
                        active.context.sessionId(),
                        AgentRunEvents.MESSAGE_START,
                        map("replyId", reply.replyId, "blockId", reply.textBlockId));
        reply.textStarted = true;
        return start.then(events.publish(
                active.context.runId(),
                active.context.sessionId(),
                AgentRunEvents.MESSAGE_DELTA,
                map(
                        "replyId", reply.replyId,
                        "blockId", reply.textBlockId,
                        "delta", delta)));
    }

    private Mono<Void> publishReasoning(ActiveExecution active, ReplyState reply, String delta) {
        if (!StringUtils.hasText(delta)
                || active.cancelled.get()
                || active.terminal.get()) {
            return Mono.empty();
        }
        return events.publish(
                active.context.runId(),
                active.context.sessionId(),
                AgentRunEvents.REASONING_DELTA,
                map(
                        "replyId", reply.replyId,
                        "blockId", reply.thinkingBlockId,
                        "delta", delta));
    }

    private Mono<Void> complete(ActiveExecution active, ReplyState reply, ChatResponse response) {
        if (active.cancelled.get() || active.terminal.get()) {
            return Mono.empty();
        }
        var message = response == null ? null : response.aiMessage();
        if (message == null) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "agent-response-invalid",
                    "聊天模型没有返回消息。"));
        }
        active.finalResult.set(response);
        var requests = normalizedRequests(message.toolExecutionRequests());
        var assistant = AiMessage.builder()
                .text(message.text())
                .thinking(message.thinking())
                .toolExecutionRequests(requests)
                .attributes(message.attributes())
                .build();
        active.messages.add(assistant);
        if (!requests.isEmpty()) {
            return requestApprovals(active, reply, message.text(), requests);
        }
        return persistAssistant(active, message).flatMap(messageId -> {
            if (!reply.textStarted && StringUtils.hasText(message.text())) {
                return publishDelta(active, reply, message.text())
                        .then(persistResult(active, message, messageId));
            }
            return persistResult(active, message, messageId);
        });
    }

    private Mono<Void> persistResult(ActiveExecution active, AiMessage message, UUID messageId) {
        var data = new LinkedHashMap<String, Object>();
        data.put("text", message.text());
        data.put("toolCalls", List.of());
        data.put("usage", usage(active.finalResult.get()));
        data.put("messageId", messageId);
        recordMemory(active, message.text());
        return finish(
                active,
                AgentRunEvents.RUN_COMPLETED,
                COMPLETED,
                null,
                null,
                data);
    }

    private Mono<Void> requestApprovals(
            ActiveExecution active, ReplyState reply, String text, List<ToolExecutionRequest> requests) {
        var emitted = new AtomicBoolean(reply.textStarted);
        var prefix = Mono.defer(() -> {
            if (emitted.compareAndSet(false, true) && StringUtils.hasText(text)) {
                return publishDelta(active, reply, text);
            }
            return Mono.empty();
        });
        active.roundToolCalls.set(requests.size());
        active.roundDenied.set(0);
        return prefix
                .thenMany(Flux.fromIterable(requests)
                        .concatMap(request -> ensureToolCall(active, request.id(), request.name())
                                .flatMap(toolCallId -> events
                                        .publish(
                                                active.context.runId(),
                                                active.context.sessionId(),
                                                AgentRunEvents.TOOL_START,
                                                map(
                                                        "replyId", reply.replyId,
                                                        "toolCallId", request.id(),
                                                        "toolName", request.name()))
                                        .then(persistApproval(
                                                active, reply.replyId, request, toolCallId)))))
                .collectList()
                .flatMap(pending -> {
                    pending.forEach(value -> active.pendingApprovals.put(
                            value.approvalId(), value));
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

    private Mono<Void> executeApproved(ActiveExecution active, PendingApproval pending) {
        var request = pending.toolCall();
        return mediaTool
                .execute(active.toolContext, arguments(request.arguments()))
                .map(JsonUtils::objectToJson)
                .map(text -> new ToolOutcome(text, "succeeded", false))
                .onErrorResume(error -> {
                    log.warn("Agent 工具执行失败：run={} tool={}", active.context.runId(), request.name(), error);
                    var message = StringUtils.hasText(error.getMessage())
                            ? error.getMessage()
                            : "工具执行失败。";
                    return Mono.just(new ToolOutcome(message, "failed", true));
                })
                .flatMap(outcome -> {
                    active.messages.add(ToolExecutionResultMessage.builder()
                            .id(request.id())
                            .toolName(request.name())
                            .text(outcome.text())
                            .isError(outcome.error())
                            .build());
                    return finishToolCall(active, request.id(), outcome.state())
                            .then(events.publish(
                                    active.context.runId(),
                                    active.context.sessionId(),
                                    AgentRunEvents.TOOL_RESULT,
                                    map(
                                            "replyId", pending.replyId(),
                                            "toolCallId", request.id(),
                                            "toolName", request.name(),
                                            "state", outcome.state())));
                });
    }

    private Mono<Void> deny(ActiveExecution active, PendingApproval pending) {
        var request = pending.toolCall();
        active.messages.add(ToolExecutionResultMessage.builder()
                .id(request.id())
                .toolName(request.name())
                .text("工具调用被拒绝。")
                .isError(true)
                .build());
        return finishToolCall(active, request.id(), "denied")
                .then(events.publish(
                        active.context.runId(),
                        active.context.sessionId(),
                        AgentRunEvents.TOOL_RESULT,
                        map(
                                "replyId", pending.replyId(),
                                "toolCallId", request.id(),
                                "toolName", request.name(),
                                "state", "denied")));
    }

    private static String systemPrompt(String rememberedContext) {
        return StringUtils.hasText(rememberedContext)
                ? SYSTEM_PROMPT + "\n\n" + rememberedContext
                : SYSTEM_PROMPT;
    }

    private void recordMemory(ActiveExecution active, String assistantText) {
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
            ActiveExecution active, String replyId, ToolExecutionRequest request, UUID toolCallId) {
        return approvals
                .findFirstByRunIdAndToolCallIdAndStatus(
                        active.context.runId(), toolCallId, "pending")
                .map(existing -> new PendingApproval(
                        existing.getId(), existing.getExpiresAt(), replyId, request))
                .switchIfEmpty(Mono.defer(() -> {
                    var approval = new AgentApprovalEntity();
                    approval.setId(UUID.randomUUID());
                    approval.setRunId(active.context.runId());
                    approval.setToolCallId(toolCallId);
                    approval.setStatus("pending");
                    var body = new LinkedHashMap<String, Object>();
                    body.put("runId", active.context.runId());
                    body.put("sessionId", active.context.sessionId());
                    body.put("toolCall", toolCall(request));
                    approval.setRequestJson(JsonUtils.objectToJson(body));
                    approval.setExpiresAt(now().plus(APPROVAL_TTL));
                    return approvals
                            .save(approval)
                            .map(saved -> new PendingApproval(
                                    saved.getId(), saved.getExpiresAt(), replyId, request));
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
        var resolvedId = toolCallIdOf(toolCallId);
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

    private Mono<Void> finishToolCall(ActiveExecution active, String toolCallId, String status) {
        var auditId = active.toolCallIds.get(toolCallIdOf(toolCallId));
        if (auditId == null) {
            return Mono.empty();
        }
        return toolCalls.findById(auditId).flatMap(entity -> {
            entity.setStatus(status);
            entity.setFinishedAt(now());
            return toolCalls.save(entity);
        }).then();
    }

    private Mono<UUID> persistAssistant(ActiveExecution active, AiMessage message) {
        if (active.cancelled.get() || active.terminal.get()) {
            return Mono.empty();
        }
        var entity = AgentMessageEntity.assistantMessage(
                active.context.sessionId(),
                active.context.runId(),
                assistantContent(message),
                List.of(),
                usage(active.finalResult.get()),
                now());
        return messages.save(entity).flatMap(saved -> requireRun(active.context.runId())
                .flatMap(run -> {
                    run.setOutputMessageId(saved.getId());
                    return runs.save(run);
                })
                .thenReturn(saved.getId()));
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
                .doFinally(ignored -> activeRuns.remove(active.context.runId(), active));
    }

    private Mono<Void> settleIncomplete() {
        return Mono.error(new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "agent-stream-incomplete",
                "模型事件流在终态前结束。"));
    }

    private Mono<Void> ensureSettled(ActiveExecution active) {
        if (active.terminal.get() || active.waitingApproval.get() || active.launchPending.get()) {
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
        var response = active.finalResult.get();
        var tokenUsage = response == null ? null : response.tokenUsage();
        if (tokenUsage == null) {
            return Mono.just(active.reservedCredits);
        }
        var input = tokens(tokenUsage.inputTokenCount());
        var output = tokens(tokenUsage.outputTokenCount());
        var total = tokens(tokenUsage.totalTokenCount());
        var cached = cachedTokens(tokenUsage);
        var usage = AiUsage.of(input, output, cached, total > 0 ? total : input + output);
        if (!usage.usable()) {
            return Mono.just(active.reservedCredits);
        }
        var tokens = new AiPricing.TokenUsage(
                BigDecimal.valueOf(usage.inputTokens()),
                BigDecimal.valueOf(usage.cachedTokens()),
                BigDecimal.valueOf(usage.outputTokens()));
        return pricing
                .quoteTokens(active.route.model().id(), tokens, now().toInstant())
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

    private static Map<String, Object> startData(ActiveExecution active, ReplyState reply) {
        return map(
                "sessionId", active.context.sessionId().toString(),
                "replyId", reply.replyId,
                "agentName", AGENT_NAME,
                "role", AGENT_ROLE);
    }

    private static Map<String, Object> approvalData(PendingApproval pending) {
        var data = new LinkedHashMap<String, Object>();
        data.put("approvalId", pending.approvalId());
        data.put("status", "pending");
        data.put("expiresAt", pending.expiresAt().toInstant());
        data.put("toolCall", toolCall(pending.toolCall()));
        return data;
    }

    private static Map<String, Object> toolCall(ToolExecutionRequest request) {
        return map(
                "id", request.id(),
                "name", request.name(),
                "input", arguments(request.arguments()),
                "state", "pending");
    }

    private static Map<String, Object> assistantContent(AiMessage message) {
        var content = new LinkedHashMap<String, Object>();
        content.put("type", "text");
        content.put("text", message.text() == null ? "" : message.text());
        return content;
    }

    private static Map<String, Object> usage(ChatResponse response) {
        var usage = response == null ? null : response.tokenUsage();
        if (usage == null) {
            return Map.of();
        }
        var input = tokens(usage.inputTokenCount());
        var output = tokens(usage.outputTokenCount());
        var total = tokens(usage.totalTokenCount());
        var cached = cachedTokens(usage);
        return AiUsage.of(input, output, cached, total > 0 ? total : input + output)
                .toMap();
    }

    private static int cachedTokens(TokenUsage usage) {
        if (usage instanceof OpenAiTokenUsage openAi
                && openAi.inputTokensDetails() != null) {
            return tokens(openAi.inputTokensDetails().cachedTokens());
        }
        if (usage instanceof AnthropicTokenUsage anthropic) {
            return tokens(anthropic.cacheReadInputTokens());
        }
        return 0;
    }

    private static int tokens(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private static List<ToolExecutionRequest> normalizedRequests(
            List<ToolExecutionRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        var normalized = new ArrayList<ToolExecutionRequest>(requests.size());
        for (var request : requests) {
            normalized.add(StringUtils.hasText(request.id())
                    ? request
                    : request.toBuilder().id(UUID.randomUUID().toString()).build());
        }
        return List.copyOf(normalized);
    }

    private String toolCallIdOf(String toolCallId) {
        return StringUtils.hasText(toolCallId) ? toolCallId.trim() : UUID.randomUUID().toString();
    }

    private static Map<String, Object> arguments(String arguments) {
        if (!StringUtils.hasText(arguments)) {
            return Map.of();
        }
        try {
            return JsonUtils.jsonToObject(arguments, OBJECT_TYPE);
        } catch (RuntimeException error) {
            log.warn("忽略无法解析的 Agent 工具参数。");
            return Map.of();
        }
    }

    private static Map<String, Object> map(Object... values) {
        var data = new LinkedHashMap<String, Object>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            if (values[index] != null && values[index + 1] != null) {
                data.put(values[index].toString(), values[index + 1]);
            }
        }
        return data;
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

    private static void dispose(ActiveExecution active) {
        active.subscription.dispose();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    private record ReservedQuota(UUID id, BigDecimal credits) {}

    private record PendingApproval(
            UUID approvalId, OffsetDateTime expiresAt, String replyId, ToolExecutionRequest toolCall) {}

    private record ToolOutcome(String text, String state, boolean error) {}

    private static final class ReplyState {

        private final String replyId = UUID.randomUUID().toString();

        private final String textBlockId = UUID.randomUUID().toString();

        private final String thinkingBlockId = UUID.randomUUID().toString();

        private boolean textStarted;
    }

    private static final class ActiveExecution {

        private final AgentRunContext context;

        private final AgentModelRoute route;

        private final StreamingChatModel model;

        private final AgentToolContext toolContext;

        private final UUID quotaReservationId;

        private final BigDecimal reservedCredits;

        private final List<ChatMessage> messages = new CopyOnWriteArrayList<>();

        private final Object approvalLock = new Object();

        private final AtomicInteger iterations = new AtomicInteger();

        private final AtomicInteger approvalsInFlight = new AtomicInteger();

        private final AtomicInteger roundToolCalls = new AtomicInteger();

        private final AtomicInteger roundDenied = new AtomicInteger();

        private final AtomicBoolean terminal = new AtomicBoolean();

        private final AtomicBoolean providerRequestSent = new AtomicBoolean();

        private final AtomicBoolean cancelled = new AtomicBoolean();

        private final AtomicBoolean waitingApproval = new AtomicBoolean();

        private final AtomicBoolean looping = new AtomicBoolean();

        private final AtomicBoolean launchPending = new AtomicBoolean();

        private final Map<String, UUID> toolCallIds = new ConcurrentHashMap<>();

        private final Map<UUID, PendingApproval> pendingApprovals = new ConcurrentHashMap<>();

        private final AtomicReference<ChatResponse> finalResult = new AtomicReference<>();

        private final Disposable.Swap subscription = Disposables.swap();

        private ActiveExecution(
                AgentRunContext context,
                AgentModelRoute route,
                StreamingChatModel model,
                AgentToolContext toolContext,
                UUID quotaReservationId,
                BigDecimal reservedCredits) {
            this.context = context;
            this.route = route;
            this.model = model;
            this.toolContext = toolContext;
            this.quotaReservationId = quotaReservationId;
            this.reservedCredits = reservedCredits;
        }
    }
}