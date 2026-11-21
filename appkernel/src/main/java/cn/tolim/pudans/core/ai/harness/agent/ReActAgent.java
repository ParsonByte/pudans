package cn.tolim.pudans.core.ai.harness.agent;

import cn.tolim.pudans.core.ai.harness.compaction.CompactionConfig;
import cn.tolim.pudans.core.ai.harness.compaction.ContextCompactor;
import cn.tolim.pudans.core.ai.harness.event.AgentEvent;
import cn.tolim.pudans.core.ai.harness.event.ToolResultBlocks;
import cn.tolim.pudans.core.ai.harness.journal.JournalEventSink;
import cn.tolim.pudans.core.ai.harness.journal.JournalEventType;
import cn.tolim.pudans.core.ai.harness.journal.ThreadJournalStore;
import cn.tolim.pudans.core.ai.harness.observability.HarnessObservationMapper;
import cn.tolim.pudans.core.ai.harness.observability.HarnessObservability;
import cn.tolim.pudans.core.ai.harness.permission.PermissionBehavior;
import cn.tolim.pudans.core.ai.harness.permission.PermissionContext;
import cn.tolim.pudans.core.ai.harness.permission.PermissionEngine;
import cn.tolim.pudans.core.ai.harness.output.StructuredOutputRequest;
import cn.tolim.pudans.core.ai.harness.output.StructuredOutputs;
import cn.tolim.pudans.core.ai.harness.plan.HarnessBuiltInTools;
import cn.tolim.pudans.core.ai.harness.skill.HarnessSkillTools;
import cn.tolim.pudans.core.ai.harness.skill.SkillRuntime;
import cn.tolim.pudans.core.ai.harness.subagent.SubagentTools;
import cn.tolim.pudans.core.ai.harness.team.TeamTools;
import cn.tolim.pudans.core.ai.harness.tool.HarnessToolkit;
import cn.tolim.pudans.core.ai.harness.tool.InMemoryToolResultStore;
import cn.tolim.pudans.core.ai.harness.tool.MetaToolFactory;
import cn.tolim.pudans.core.ai.harness.tool.ToolPatterns;
import cn.tolim.pudans.core.ai.harness.tool.ToolResultEvictionConfig;
import cn.tolim.pudans.core.ai.harness.tool.ToolResultStore;
import cn.tolim.pudans.core.ai.provider.langchain4j.LangChain4jToolMapper;
import cn.tolim.pudans.core.ai.rag.KnowledgeBaseRetrievalTool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.json.JsonMapper;

public final class ReActAgent implements Agent {

    private static final int DEFAULT_MAX_ITERS = 10;

    private static final Duration DEFAULT_CONFIRMATION_TIMEOUT = Duration.ofMinutes(5);

    private final String name;

    private final String systemPrompt;

    private final ChatModel chatModel;

    private final @Nullable StreamingChatModel streamingChatModel;

    private final HarnessToolkit toolkit;

    private final AgentStateStore stateStore;

    private final @Nullable ThreadJournalStore journalStore;

    private final int maxIters;

    private final Double temperature;

    private final Integer maxOutputTokens;

    private final @Nullable Double topP;

    private final @Nullable Integer topK;

    private final List<String> stopSequences;

    private final PermissionEngine permissionEngine;

    private final Duration confirmationTimeout;

    private final boolean planModeEnabled;

    private final boolean todoEnabled;

    private final boolean toolSearchEnabled;

    private final List<String> externalToolPatterns;

    private final @Nullable ContextCompactor compactor;

    private final ToolResultEvictionConfig evictionConfig;

    private final ToolResultStore toolResultStore;

    private final @Nullable SkillRuntime skillRuntime;

    private final @Nullable SubagentTools subagentTools;

    private final @Nullable TeamTools teamTools;

    private final @Nullable KnowledgeBaseRetrievalTool knowledgeBaseRetrievalTool;

    private final @Nullable HarnessObservationMapper observationMapper;

    private ReActAgent(Builder builder) {
        this.name = Objects.requireNonNull(builder.name, "Agent 名称不能为空");
        this.systemPrompt = builder.systemPrompt == null ? "" : builder.systemPrompt;
        this.chatModel = Objects.requireNonNull(builder.chatModel, "ChatModel 不能为空");
        this.streamingChatModel = builder.streamingChatModel;
        this.toolkit = builder.toolkit == null ? new HarnessToolkit() : builder.toolkit;
        this.stateStore = builder.stateStore == null ? new InMemoryAgentStateStore() : builder.stateStore;
        this.journalStore = builder.journalStore;
        this.maxIters = builder.maxIters <= 0 ? DEFAULT_MAX_ITERS : builder.maxIters;
        this.temperature = builder.temperature;
        this.maxOutputTokens = builder.maxOutputTokens;
        this.topP = builder.topP;
        this.topK = builder.topK;
        this.stopSequences = builder.stopSequences == null ? List.of() : List.copyOf(builder.stopSequences);
        this.permissionEngine = builder.permissionEngine == null
                ? new PermissionEngine(PermissionContext.builder().build())
                : builder.permissionEngine;
        this.confirmationTimeout = builder.confirmationTimeout == null
                ? DEFAULT_CONFIRMATION_TIMEOUT
                : builder.confirmationTimeout;
        this.planModeEnabled = builder.planModeEnabled;
        this.todoEnabled = builder.todoEnabled;
        this.toolSearchEnabled = builder.toolSearchEnabled;
        this.externalToolPatterns = builder.externalToolPatterns == null
                ? List.of()
                : List.copyOf(builder.externalToolPatterns);
        this.compactor = builder.compaction != null && builder.compaction.enabled()
                ? new ContextCompactor(chatModel, builder.compaction)
                : null;
        this.evictionConfig = builder.evictionConfig == null
                ? ToolResultEvictionConfig.disabled()
                : builder.evictionConfig;
        this.toolResultStore = builder.toolResultStore == null
                ? new InMemoryToolResultStore()
                : builder.toolResultStore;
        this.skillRuntime = builder.skillRuntime;
        this.subagentTools = builder.subagentTools;
        this.teamTools = builder.teamTools;
        this.knowledgeBaseRetrievalTool = builder.knowledgeBaseRetrievalTool;
        this.observationMapper = builder.observability == null
                ? null
                : new HarnessObservationMapper(builder.observability);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Mono<AgentResult> call(String input, RuntimeContext context) {
        return start(input, context).result();
    }

    @Override
    public AgentRun start(String input, RuntimeContext context) {
        return start(UserMessage.from(input), context, false);
    }

    public AgentRun start(UserMessage input, RuntimeContext context, boolean streaming) {
        var run = new AgentRun("run-" + UUID.randomUUID());
        if (observationMapper != null) {
            run.onEvent(observationMapper::onEvent);
        }
        run.attach(execute(input, context, run, streaming));
        return run;
    }

    public <T> Mono<T> call(String input, Class<T> type, RuntimeContext context) {
        return new StructuredOutputs(JsonMapper.shared())
                .generate(chatModel, StructuredOutputRequest.of(systemPrompt, input), type);
    }

    private Mono<AgentResult> execute(
            UserMessage input, RuntimeContext context, AgentRun run, boolean streaming) {
        run.emit(new AgentEvent.AgentStart(run.id(), name));
        var journal = journalStore == null
                ? Mono.just(JournalEventSink.disabled())
                : journalStore.open(context).map(JournalEventSink::new);
        return journal.flatMap(sink -> execute(input, context, run, streaming, sink));
    }

    private Mono<AgentResult> execute(
            UserMessage input,
            RuntimeContext context,
            AgentRun run,
            boolean streaming,
            JournalEventSink journal) {
        journal.record(
                JournalEventType.RUN_START, run.id(), Map.of("agentName", name));
        var journalClosed = new AtomicBoolean();
        var loaded = stateStore instanceof VersionedAgentStateStore versioned
                ? versioned
                        .loadVersioned(context.userId(), context.sessionId())
                        .map(value -> new LoadedState(value.state(), value.version()))
                : stateStore
                        .load(context.userId(), context.sessionId())
                        .map(state -> new LoadedState(state, -1L));
        return loaded
                .defaultIfEmpty(new LoadedState(new AgentState(), -1L))
                .flatMap(loadedState -> {
                    var state = loadedState.state();
                    var version = new AtomicLong(loadedState.version());
                    closeDanglingToolCalls(run, state);
                    state.addMessage(input);
                    journal.checkpoint(state, "run-start");
                    var compacted = compactor == null ? Mono.just(false) : compactor.compactIfNeeded(state);
                    var execution = compacted
                            .flatMap(didCompact -> {
                                if (didCompact) {
                                    run.emit(new AgentEvent.HintBlock(
                                            run.id(), "历史上下文已压缩。"));
                                }
                                return Mono.defer(() -> loop(
                                        state, context, run, 0, streaming, journal, version, false));
                            })
                            .map(result -> {
                                journal.record(
                                        JournalEventType.RUN_OUTPUT,
                                        run.id(),
                                        Map.of("content", result.content()));
                                journal.checkpoint(state, "run-end");
                                journal.record(
                                        JournalEventType.RUN_END,
                                        run.id(),
                                        Map.of("status", "completed"));
                                return result;
                            });
                    return execution
                            .flatMap(result -> {
                                journalClosed.set(true);
                                return journal.flush().thenReturn(result);
                            })
                            .onErrorResume(error -> {
                                if (!journalClosed.get()) {
                                    journal.record(
                                            JournalEventType.RUN_END,
                                            run.id(),
                                            Map.of(
                                                    "status", "failed",
                                                    "message", errorMessage(error)));
                                }
                                return saveState(state, context, version)
                                        .onErrorResume(saveError -> {
                                            error.addSuppressed(saveError);
                                            return Mono.empty();
                                        })
                                        .then(journal.flush()
                                                .onErrorResume(flushError -> {
                                                    error.addSuppressed(flushError);
                                                    return Mono.empty();
                                                }))
                                        .then(Mono.error(error));
                            });
                })
                .doOnNext(result -> run.emit(new AgentEvent.AgentResult(run.id(), result.content())))
                .doFinally(signal -> run.emit(new AgentEvent.AgentEnd(run.id(), name)));
    }

    private Mono<Void> saveState(AgentState state, RuntimeContext context, AtomicLong version) {
        if (!(stateStore instanceof VersionedAgentStateStore versioned)) {
            return stateStore.save(context.userId(), context.sessionId(), state);
        }
        var current = version.get();
        if (current < 0) {
            return stateStore
                    .save(context.userId(), context.sessionId(), state)
                    .then(versioned.loadVersioned(context.userId(), context.sessionId()))
                    .doOnNext(loaded -> version.set(loaded.version()))
                    .then();
        }
        return versioned
                .saveIfVersion(context.userId(), context.sessionId(), state, current)
                .switchIfEmpty(Mono.error(new AgentStateConflictException("Agent 状态版本冲突，请重试。")))
                .doOnNext(version::set)
                .then();
    }

    private static void closeDanglingToolCalls(AgentRun run, AgentState state) {
        var messages = state.messages();
        AiMessage last = null;
        for (var index = messages.size() - 1; index >= 0; index--) {
            if (messages.get(index) instanceof AiMessage message
                    && !message.toolExecutionRequests().isEmpty()) {
                last = message;
                break;
            }
        }
        if (last == null) {
            return;
        }
        var completed = messages.stream()
                .filter(ToolExecutionResultMessage.class::isInstance)
                .map(ToolExecutionResultMessage.class::cast)
                .map(ToolExecutionResultMessage::id)
                .collect(Collectors.toSet());
        for (var call : last.toolExecutionRequests()) {
            if (!completed.contains(call.id())) {
                state.addMessage(ToolExecutionResultMessage.builder()
                        .id(call.id())
                        .toolName(call.name())
                        .text("上次运行在工具完成前中断。")
                        .isError(true)
                        .build());
                run.emit(new AgentEvent.ToolCompensated(
                        run.id(), call.id(), call.name(), "上次运行中断，已补偿为错误结果。"));
            }
        }
    }

    private record LoadedState(AgentState state, long version) {}

    private static String errorMessage(Throwable error) {
        var message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private Mono<AgentResult> loop(
            AgentState state,
            RuntimeContext context,
            AgentRun run,
            int iteration,
            boolean streaming,
            JournalEventSink journal,
            AtomicLong version,
            boolean overflowRetried) {
        if (run.cancelled()) {
            run.emit(new AgentEvent.RequestStop(run.id()));
            return Mono.error(new AgentInterruptedException("Agent 运行已中断。"));
        }
        if (iteration >= maxIters) {
            run.emit(new AgentEvent.ExceedMaxIters(run.id(), maxIters));
            return saveState(state, context, version)
                    .then(Mono.error(new IllegalStateException(
                            "Agent 超过最大迭代次数：" + maxIters + "。")));
        }
        var requestBuilder = ChatRequest.builder()
                .messages(messages(state))
                .toolSpecifications(specifications());
        if (temperature != null) {
            requestBuilder.temperature(temperature);
        }
        if (maxOutputTokens != null) {
            requestBuilder.maxOutputTokens(maxOutputTokens);
        }
        if (topP != null) {
            requestBuilder.topP(topP);
        }
        if (topK != null) {
            requestBuilder.topK(topK);
        }
        if (!stopSequences.isEmpty()) {
            requestBuilder.stopSequences(stopSequences);
        }
        var request = requestBuilder.build();
        run.emit(new AgentEvent.ModelCallStart(run.id(), iteration));
        journal.modelCallStart(run.id(), iteration);
        return modelCall(request, run, streaming)
                .flatMap(call -> {
                    var response = call.response();
                    var finishReason =
                            response.finishReason() == null ? null : response.finishReason().name();
                    run.emit(new AgentEvent.ModelCallEnd(run.id(), iteration, finishReason));
                    journal.modelCallEnd(run.id(), iteration, finishReason);
                    state.addMessage(response.aiMessage());
                    if (!call.streamed()) {
                        emitText(run, response.aiMessage());
                        emitThinking(run, response.aiMessage());
                    }
                    var toolCalls = response.aiMessage().toolExecutionRequests();
                    if (toolCalls.isEmpty()) {
                        return saveState(state, context, version)
                                .thenReturn(new AgentResult(response.aiMessage().text(), response));
                    }
                    return Flux.fromIterable(toolCalls)
                            .concatMap(toolCall ->
                                    executeToolCall(state, context, run, toolCall, journal, version))
                            .then(Mono.defer(() ->
                                    loop(state, context, run, iteration + 1, streaming, journal, version, false)));
                })
                .onErrorResume(error -> retryAfterCompaction(
                        error,
                        state,
                        context,
                        run,
                        iteration,
                        streaming,
                        journal,
                        version,
                        overflowRetried));
    }

    private Mono<AgentResult> retryAfterCompaction(
            Throwable error,
            AgentState state,
            RuntimeContext context,
            AgentRun run,
            int iteration,
            boolean streaming,
            JournalEventSink journal,
            AtomicLong version,
            boolean overflowRetried) {
        if (overflowRetried || compactor == null || !isContextOverflow(error)) {
            return Mono.error(error);
        }
        return compactor
                .compactNow(state)
                .onErrorResume(compactionError -> {
                    error.addSuppressed(compactionError);
                    return Mono.just(false);
                })
                .flatMap(compacted -> {
                    if (!compacted) {
                        return Mono.error(error);
                    }
                    run.emit(new AgentEvent.HintBlock(run.id(), "上下文溢出，已压缩后重试。"));
                    return loop(state, context, run, iteration, streaming, journal, version, true);
                });
    }

    private static boolean isContextOverflow(Throwable error) {
        for (var current = error; current != null; current = current.getCause()) {
            var message = current.getMessage();
            if (message == null) {
                continue;
            }
            var lower = message.toLowerCase(Locale.ROOT);
            if (lower.contains("context length")
                    || lower.contains("context window")
                    || lower.contains("maximum context")
                    || lower.contains("prompt is too long")
                    || lower.contains("too many tokens")
                    || lower.contains("token limit")) {
                return true;
            }
        }
        return false;
    }

    private Mono<ModelCall> modelCall(ChatRequest request, AgentRun run, boolean streaming) {
        if (streaming && streamingChatModel != null) {
            return streamCall(request, run).map(response -> new ModelCall(response, true));
        }
        return Mono.fromCallable(() -> new ModelCall(chatModel.chat(request), false))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<ChatResponse> streamCall(ChatRequest request, AgentRun run) {
        var text = new StringBuilder();
        var thinking = new StringBuilder();
        var textStarted = new AtomicBoolean();
        var thinkingStarted = new AtomicBoolean();
        return Mono.create(sink -> streamingChatModel.chat(
                request,
                new StreamingChatResponseHandler() {

                    @Override
                    public void onPartialResponse(String partial) {
                        if (textStarted.compareAndSet(false, true)) {
                            run.emit(new AgentEvent.TextBlockStart(run.id()));
                        }
                        text.append(partial);
                        run.emit(new AgentEvent.TextBlockDelta(run.id(), partial));
                    }

                    @Override
                    public void onPartialToolCall(PartialToolCall partial) {
                        run.emit(new AgentEvent.ToolCallDelta(
                                run.id(), partial.id(), partial.partialArguments()));
                    }

                    @Override
                    public void onPartialThinking(PartialThinking partial) {
                        if (thinkingStarted.compareAndSet(false, true)) {
                            run.emit(new AgentEvent.ThinkingBlockStart(run.id()));
                        }
                        thinking.append(partial.text());
                        run.emit(new AgentEvent.ThinkingBlockDelta(run.id(), partial.text()));
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse response) {
                        var message = response.aiMessage();
                        if (!textStarted.get()
                                && message != null
                                && message.text() != null
                                && !message.text().isBlank()) {
                            textStarted.set(true);
                            text.append(message.text());
                            run.emit(new AgentEvent.TextBlockStart(run.id()));
                            run.emit(new AgentEvent.TextBlockDelta(run.id(), message.text()));
                        }
                        if (!thinkingStarted.get()
                                && message != null
                                && message.thinking() != null
                                && !message.thinking().isBlank()) {
                            thinkingStarted.set(true);
                            thinking.append(message.thinking());
                            run.emit(new AgentEvent.ThinkingBlockStart(run.id()));
                            run.emit(new AgentEvent.ThinkingBlockDelta(
                                    run.id(), message.thinking()));
                        }
                        if (textStarted.get()) {
                            run.emit(new AgentEvent.TextBlockEnd(run.id(), text.toString()));
                        }
                        if (thinkingStarted.get()) {
                            run.emit(new AgentEvent.ThinkingBlockEnd(run.id(), thinking.toString()));
                        }
                        sink.success(response);
                    }

                    @Override
                    public void onError(Throwable error) {
                        sink.error(error);
                    }
                }));
    }

    private record ModelCall(ChatResponse response, boolean streamed) {}

    private Mono<Void> executeToolCall(
            AgentState state,
            RuntimeContext context,
            AgentRun run,
            ToolExecutionRequest call,
            JournalEventSink journal,
            AtomicLong version) {
        var id = call.id() == null ? UUID.randomUUID().toString() : call.id();
        var arguments = call.arguments() == null ? "" : call.arguments();
        journal.toolCallStart(run.id(), id, call.name(), arguments);
        return executeToolCallCore(state, context, run, call, id, arguments, journal, version)
                .then(Mono.fromRunnable(
                        () -> recordToolResult(state, id, call.name(), run, journal)));
    }

    private static void recordToolResult(
            AgentState state, String id, String name, AgentRun run, JournalEventSink journal) {
        state.messages().stream()
                .filter(ToolExecutionResultMessage.class::isInstance)
                .map(ToolExecutionResultMessage.class::cast)
                .filter(message -> id.equals(message.id()))
                .findFirst()
                .ifPresent(message ->
                        journal.toolResultEnd(run.id(), id, name, message.text(), message.isError()));
    }

    private Mono<Void> executeToolCallCore(
            AgentState state,
            RuntimeContext context,
            AgentRun run,
            ToolExecutionRequest call,
            String id,
            String arguments,
            JournalEventSink journal,
            AtomicLong version) {
        run.emit(new AgentEvent.ToolCallStart(run.id(), id, call.name(), arguments));
        run.emit(new AgentEvent.ToolCallEnd(run.id(), id, call.name(), arguments));
        if (knowledgeBaseRetrievalTool != null
                && KnowledgeBaseRetrievalTool.NAME.equals(call.name())) {
            return Mono.fromCallable(() ->
                            knowledgeBaseRetrievalTool.handler().execute(call, argumentsNode(call)))
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap(result -> appendToolResult(run, state, id, call.name(), result));
        }
        if (subagentTools != null && isSubagentTool(call.name())) {
            var subagentArgs = LangChain4jToolMapper.arguments(call.arguments());
            var subagentName = subagentArgs.path("agent_id").asText("");
            if (subagentName.isBlank()) {
                subagentName = subagentArgs.path("label").asText("");
            }
            run.emit(new AgentEvent.SubagentExposed(run.id(), subagentName));
            return subagentTools
                    .execute(call.name(), subagentArgs, context)
                    .flatMap(result -> appendToolResult(run, state, id, call.name(), result));
        }
        if (teamTools != null && TeamTools.TEAM.equals(call.name())) {
            return teamTools
                    .execute(call.name(), LangChain4jToolMapper.arguments(call.arguments()), context)
                    .flatMap(result -> appendToolResult(run, state, id, call.name(), result));
        }
        if (skillRuntime != null
                && HarnessSkillTools.SKILL.equals(call.name())
                && !skillRuntime.catalog().isEmpty()) {
            var result = HarnessSkillTools.execute(
                    LangChain4jToolMapper.arguments(call.arguments()), skillRuntime);
            return appendToolResult(run, state, id, call.name(), result);
        }
        if (toolSearchEnabled && MetaToolFactory.TOOL_SEARCH.equals(call.name())) {
            var result = new MetaToolFactory(toolkit.groupManager())
                    .searchTools(toolkit, LangChain4jToolMapper.arguments(call.arguments()));
            return appendToolResult(run, state, id, call.name(), result);
        }
        if (MetaToolFactory.TOOL_TASK.equals(call.name())) {
            return new MetaToolFactory(toolkit.groupManager())
                    .asyncTask(toolkit.asyncRegistry(), LangChain4jToolMapper.arguments(call.arguments()))
                    .flatMap(result -> appendToolResult(run, state, id, call.name(), result));
        }
        if (MetaToolFactory.RESET_TOOLS.equals(call.name())) {
            var result = new MetaToolFactory(toolkit.groupManager())
                    .resetTools(LangChain4jToolMapper.arguments(call.arguments()));
            return appendToolResult(run, state, id, call.name(), result);
        }
        if (HarnessBuiltInTools.isBuiltIn(call.name())) {
            var builtIn = HarnessBuiltInTools.execute(
                    call.name(), LangChain4jToolMapper.arguments(call.arguments()), state);
            return appendToolResult(
                    run,
                    state,
                    id,
                    call.name(),
                    new HarnessToolkit.ToolResult(builtIn.output(), builtIn.error()));
        }
        if (HarnessBuiltInTools.isReadToolResult(call.name())) {
            var resultId = LangChain4jToolMapper.arguments(call.arguments()).path("id").asText("");
            return toolResultStore
                    .read(resultId)
                    .map(Optional::of)
                    .defaultIfEmpty(Optional.<String>empty())
                    .flatMap(stored -> {
                        var error = stored.isEmpty();
                        var text = error ? "工具结果不存在：" + resultId : stored.get();
                        return appendToolResult(
                                run, state, id, call.name(), new HarnessToolkit.ToolResult(text, error));
                    });
        }
        if (state.planMode().active() && !HarnessBuiltInTools.allowedInPlanMode(call.name())) {
            run.emit(new AgentEvent.AllToolsDenied(run.id()));
            journal.toolDecision(run.id(), id, "plan-mode-denied");
            return appendToolResult(
                    run,
                    state,
                    id,
                    call.name(),
                    new HarnessToolkit.ToolResult("计划模式只允许只读工具和 plan_*/todo_write。", true));
        }
        var decision = permissionEngine.check(call);
        if (decision.behavior() == PermissionBehavior.DENY) {
            run.emit(new AgentEvent.AllToolsDenied(run.id()));
            journal.toolDecision(run.id(), id, "denied");
            return appendToolResult(
                    run, state, id, call.name(), new HarnessToolkit.ToolResult(decision.message(), true));
        }
        if (decision.behavior() == PermissionBehavior.ASK) {
            run.emit(new AgentEvent.RequireUserConfirm(
                    run.id(), id, call.name(), decision.message()));
            journal.toolDecision(run.id(), id, "ask");
            return saveState(state, context, version)
                    .then(run.awaitConfirmation(id)
                            .timeout(confirmationTimeout, Mono.just(false)))
                    .flatMap(approved -> {
                        if (!approved) {
                            journal.toolDecision(run.id(), id, "user-denied");
                            return appendToolResult(
                                    run,
                                    state,
                                    id,
                                    call.name(),
                                    new HarnessToolkit.ToolResult(
                                            "用户拒绝了工具调用：" + call.name(), true));
                        }
                        return executeToolOrExternal(
                                state, context, run, call, id, journal, version);
                    });
        }
        return executeToolOrExternal(state, context, run, call, id, journal, version);
    }

    private Mono<Void> executeToolOrExternal(
            AgentState state,
            RuntimeContext context,
            AgentRun run,
            ToolExecutionRequest call,
            String id,
            JournalEventSink journal,
            AtomicLong version) {
        if (!ToolPatterns.matches(externalToolPatterns, call.name())) {
            return executeTool(state, run, call, id);
        }
        run.emit(new AgentEvent.RequireExternalExecution(
                run.id(), id, call.name(), call.arguments()));
        run.emit(new AgentEvent.ToolResultProgress(run.id(), id, "等待外部执行结果。"));
        journal.toolDecision(run.id(), id, "external");
        return saveState(state, context, version)
                .then(run.awaitExternalResult(id)
                        .timeout(confirmationTimeout, Mono.fromSupplier(() -> {
                            var timeout = new AgentRun.ExternalResult(
                                    "外部执行超时，结果未知。", true);
                            run.emit(new AgentEvent.ExternalExecutionResult(
                                    run.id(), id, timeout.output(), timeout.error()));
                            return timeout;
                        })))
                .flatMap(result -> appendToolResult(
                        run,
                        state,
                        id,
                        call.name(),
                        new HarnessToolkit.ToolResult(result.output(), result.error())));
    }

    private Mono<Void> executeTool(
            AgentState state, AgentRun run, ToolExecutionRequest call, String id) {
        run.emit(new AgentEvent.ToolResultProgress(run.id(), id, "正在执行工具 " + call.name() + "。"));
        return Mono.fromCallable(() -> toolkit.execute(call))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(result -> evict(run, call.name(), result.output())
                        .flatMap(output -> appendToolResult(
                                run,
                                state,
                                id,
                                call.name(),
                                new HarnessToolkit.ToolResult(output, result.error()))));
    }

    private static void emitText(AgentRun run, AiMessage message) {
        var text = message.text();
        if (text == null || text.isBlank()) {
            return;
        }
        run.emit(new AgentEvent.TextBlockStart(run.id()));
        run.emit(new AgentEvent.TextBlockDelta(run.id(), text));
        run.emit(new AgentEvent.TextBlockEnd(run.id(), text));
    }

    private static void emitThinking(AgentRun run, AiMessage message) {
        var text = message.thinking();
        if (text == null || text.isBlank()) {
            return;
        }
        run.emit(new AgentEvent.ThinkingBlockStart(run.id()));
        run.emit(new AgentEvent.ThinkingBlockDelta(run.id(), text));
        run.emit(new AgentEvent.ThinkingBlockEnd(run.id(), text));
    }

    private Mono<String> evict(AgentRun run, String toolName, String output) {
        if (!evictionConfig.enabled() || output == null || output.length() <= evictionConfig.maxChars()) {
            return Mono.just(output == null ? "" : output);
        }
        return toolResultStore
                .save(run.id(), toolName, output)
                .map(id -> "[工具输出过大，已卸载为 " + id + "，可使用 read_tool_result 读取。]");
    }

    private static Mono<Void> appendToolResult(
            AgentRun run, AgentState state, String id, String toolName, HarnessToolkit.ToolResult result) {
        var output = result.output() == null ? "" : result.output();
        run.emit(new AgentEvent.ToolResultProgress(run.id(), id, "工具执行完成，正在整理结果。"));
        if (!output.isBlank()) {
            run.emit(new AgentEvent.ToolResultTextDelta(run.id(), id, clipToolOutput(output)));
        }
        ToolResultBlocks.emit(run, output);
        if (result.error()) {
            run.emit(new AgentEvent.ToolCompensated(
                    run.id(), id, toolName, "工具返回错误，已记录为失败结果。"));
        }
        run.emit(new AgentEvent.ToolResultStart(run.id(), id, toolName));
        run.emit(new AgentEvent.ToolResultEnd(
                run.id(), id, toolName, output, result.error()));
        appendToolResult(state, id, toolName, output, result.error());
        return Mono.empty();
    }

    private static String clipToolOutput(String output) {
        var limit = 4000;
        if (output.length() <= limit) {
            return output;
        }
        var cut = limit;
        if (Character.isHighSurrogate(output.charAt(cut - 1))) {
            cut--;
        }
        return output.substring(0, cut) + "…";
    }

    private static void appendToolResult(
            AgentState state, String id, String toolName, String output, boolean error) {
        state.addMessage(ToolExecutionResultMessage.builder()
                .id(id)
                .toolName(toolName)
                .text(output)
                .isError(error)
                .build());
    }

    private static com.fasterxml.jackson.databind.JsonNode argumentsNode(
            ToolExecutionRequest call) {
        return LangChain4jToolMapper.arguments(call.arguments());
    }

    private static boolean isSubagentTool(String toolName) {
        return SubagentTools.AGENT_SPAWN.equals(toolName)
                || SubagentTools.AGENT_SEND.equals(toolName)
                || SubagentTools.AGENT_LIST.equals(toolName)
                || SubagentTools.AGENT_RELEASE.equals(toolName);
    }

    private List<ToolSpecification> specifications() {
        var specifications = new ArrayList<ToolSpecification>(toolkit.activeSpecifications());
        specifications.addAll(HarnessBuiltInTools.specifications(planModeEnabled, todoEnabled));
        if (evictionConfig.enabled()) {
            specifications.add(HarnessBuiltInTools.readToolResultSpecification());
        }
        if (skillRuntime != null) {
            specifications.add(HarnessSkillTools.specification());
        }
        if (subagentTools != null) {
            specifications.addAll(LangChain4jToolMapper.toSpecifications(subagentTools.definitions()));
        }
        if (teamTools != null) {
            specifications.addAll(LangChain4jToolMapper.toSpecifications(teamTools.definitions()));
        }
        if (knowledgeBaseRetrievalTool != null) {
            specifications.addAll(LangChain4jToolMapper.toSpecifications(
                    List.of(knowledgeBaseRetrievalTool.definition())));
        }
        if (toolSearchEnabled) {
            specifications.addAll(LangChain4jToolMapper.toSpecifications(
                    List.of(MetaToolFactory.toolSearchTool())));
        }
        if (toolkit.hasAsyncTools()) {
            specifications.addAll(LangChain4jToolMapper.toSpecifications(
                    List.of(MetaToolFactory.toolTaskTool())));
        }
        if (!toolkit.groupManager().metaGroupNames().isEmpty()) {
            var resetTool = new MetaToolFactory(toolkit.groupManager()).resetToolsTool();
            specifications.addAll(LangChain4jToolMapper.toSpecifications(List.of(resetTool)));
        }
        return List.copyOf(specifications);
    }

    private List<ChatMessage> messages(AgentState state) {
        var messages = new ArrayList<ChatMessage>();
        var system = systemText(state);
        if (!system.isBlank()) {
            messages.add(SystemMessage.from(system));
        }
        messages.addAll(state.messages());
        return List.copyOf(messages);
    }

    private String systemText(AgentState state) {
        var sections = new ArrayList<String>();
        if (!systemPrompt.isBlank()) {
            sections.add(systemPrompt);
        }
        if (!state.summary().isBlank()) {
            sections.add("【历史摘要】\n" + state.summary());
        }
        if (state.planMode().active() && !state.planMode().planContent().isBlank()) {
            sections.add("【当前计划】\n" + state.planMode().planContent());
        }
        if (skillRuntime != null) {
            var skills = skillRuntime.prompt();
            if (!skills.isBlank()) {
                sections.add(skills);
            }
        }
        if (!state.todos().isEmpty()) {
            var todos = new StringBuilder("【任务清单】");
            for (var todo : state.todos()) {
                todos.append("\n- [").append(todo.status()).append("] ").append(todo.content());
            }
            sections.add(todos.toString());
        }
        return String.join("\n\n", sections);
    }

    public static final class Builder {

        private String name;

        private String systemPrompt;

        private ChatModel chatModel;

        private StreamingChatModel streamingChatModel;

        private HarnessToolkit toolkit;

        private AgentStateStore stateStore;

        private ThreadJournalStore journalStore;

        private int maxIters = DEFAULT_MAX_ITERS;

        private Double temperature;

        private Integer maxOutputTokens;

        private Double topP;

        private Integer topK;

        private List<String> stopSequences;

        private PermissionEngine permissionEngine;

        private Duration confirmationTimeout;

        private boolean planModeEnabled;

        private boolean todoEnabled;

        private boolean toolSearchEnabled = true;

        private List<String> externalToolPatterns;

        private CompactionConfig compaction;

        private ToolResultEvictionConfig evictionConfig;

        private ToolResultStore toolResultStore;

        private SkillRuntime skillRuntime;

        private SubagentTools subagentTools;

        private TeamTools teamTools;

        private KnowledgeBaseRetrievalTool knowledgeBaseRetrievalTool;

        private HarnessObservability observability;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder chatModel(ChatModel chatModel) {
            this.chatModel = chatModel;
            return this;
        }

        public Builder streamingChatModel(StreamingChatModel streamingChatModel) {
            this.streamingChatModel = streamingChatModel;
            return this;
        }

        public Builder toolkit(HarnessToolkit toolkit) {
            this.toolkit = toolkit;
            return this;
        }

        public Builder stateStore(AgentStateStore stateStore) {
            this.stateStore = stateStore;
            return this;
        }

        public Builder journalStore(ThreadJournalStore journalStore) {
            this.journalStore = journalStore;
            return this;
        }

        public Builder maxIters(int maxIters) {
            this.maxIters = maxIters;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public Builder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        public Builder permissionEngine(PermissionEngine permissionEngine) {
            this.permissionEngine = permissionEngine;
            return this;
        }

        public Builder confirmationTimeout(Duration confirmationTimeout) {
            this.confirmationTimeout = confirmationTimeout;
            return this;
        }

        public Builder planModeEnabled(boolean planModeEnabled) {
            this.planModeEnabled = planModeEnabled;
            return this;
        }

        public Builder todoEnabled(boolean todoEnabled) {
            this.todoEnabled = todoEnabled;
            return this;
        }

        public Builder toolSearchEnabled(boolean toolSearchEnabled) {
            this.toolSearchEnabled = toolSearchEnabled;
            return this;
        }

        public Builder externalToolPatterns(List<String> externalToolPatterns) {
            this.externalToolPatterns = externalToolPatterns;
            return this;
        }

        public Builder compaction(CompactionConfig compaction) {
            this.compaction = compaction;
            return this;
        }

        public Builder toolResultEviction(ToolResultEvictionConfig evictionConfig) {
            this.evictionConfig = evictionConfig;
            return this;
        }

        public Builder toolResultStore(ToolResultStore toolResultStore) {
            this.toolResultStore = toolResultStore;
            return this;
        }

        public Builder skillRuntime(SkillRuntime skillRuntime) {
            this.skillRuntime = skillRuntime;
            return this;
        }

        public Builder subagentTools(SubagentTools subagentTools) {
            this.subagentTools = subagentTools;
            return this;
        }

        public Builder teamTools(TeamTools teamTools) {
            this.teamTools = teamTools;
            return this;
        }

        public Builder knowledgeBaseRetrievalTool(KnowledgeBaseRetrievalTool tool) {
            this.knowledgeBaseRetrievalTool = tool;
            return this;
        }

        public Builder observability(HarnessObservability observability) {
            this.observability = observability;
            return this;
        }

        public ReActAgent build() {
            return new ReActAgent(this);
        }
    }
}
