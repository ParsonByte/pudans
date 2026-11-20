package cn.tolim.pudans.core.ai.chat;

import cn.tolim.pudans.contract.core.extension.ai.AiAgent;
import cn.tolim.pudans.contract.core.extension.ai.AiChatMode;
import cn.tolim.pudans.contract.core.extension.ai.AiHook;
import cn.tolim.pudans.contract.core.extension.ai.AiSetting;
import cn.tolim.pudans.contract.core.extension.ai.KnowledgeBase;
import cn.tolim.pudans.contract.core.extension.storage.StorageFileState;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.agent.GatewayChatContext;
import cn.tolim.pudans.core.ai.agent.GatewayChatMessageMapper;
import cn.tolim.pudans.core.ai.agent.GatewayChatModel;
import cn.tolim.pudans.core.ai.agent.GatewayRunState;
import cn.tolim.pudans.core.ai.agent.GatewayStreamingChatModel;
import cn.tolim.pudans.core.ai.config.AiChatModeService;
import cn.tolim.pudans.core.ai.config.AiSettingService;
import cn.tolim.pudans.core.ai.embedding.AiEmbeddingService;
import cn.tolim.pudans.core.ai.conversation.AiConversationService;
import cn.tolim.pudans.core.ai.gateway.AiGatewayMeta;
import cn.tolim.pudans.core.ai.gateway.AiGatewayService;
import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.ir.AiRequestParams;
import cn.tolim.pudans.core.ai.gateway.ir.AiTool;
import cn.tolim.pudans.core.ai.gateway.session.AiGatewaySessionService;
import cn.tolim.pudans.core.ai.harness.agent.AgentResult;
import cn.tolim.pudans.core.ai.harness.agent.AgentRun;
import cn.tolim.pudans.core.ai.harness.agent.AgentState;
import cn.tolim.pudans.core.ai.harness.agent.AgentStateStore;
import cn.tolim.pudans.core.ai.harness.agent.HarnessAgent;
import cn.tolim.pudans.core.ai.harness.agent.RuntimeContext;
import cn.tolim.pudans.core.ai.harness.event.AgentEvent;
import cn.tolim.pudans.core.ai.harness.journal.ThreadJournalStore;
import cn.tolim.pudans.core.ai.harness.permission.PermissionContext;
import cn.tolim.pudans.core.ai.harness.permission.PermissionEngine;
import cn.tolim.pudans.core.ai.harness.tool.HarnessToolkit;
import cn.tolim.pudans.core.ai.library.command.SlashCommandService;
import cn.tolim.pudans.core.ai.library.hook.HookService;
import cn.tolim.pudans.core.ai.memory.AiMemoryContextBuilder;
import cn.tolim.pudans.core.ai.memory.MemoryContext;
import cn.tolim.pudans.core.ai.memory.MemoryInjection;
import cn.tolim.pudans.core.ai.memory.task.MemoryTaskScheduler;
import cn.tolim.pudans.core.ai.prompt.ChatPrompts;
import cn.tolim.pudans.core.ai.provider.langchain4j.LangChain4jToolMapper;
import cn.tolim.pudans.core.ai.rag.Citation;
import cn.tolim.pudans.core.ai.rag.DocumentParser;
import cn.tolim.pudans.core.ai.rag.KnowledgeBaseAcl;
import cn.tolim.pudans.core.ai.rag.KnowledgeBaseContentRetriever;
import cn.tolim.pudans.core.ai.rag.KnowledgeBaseRetrievalService;
import cn.tolim.pudans.core.ai.rag.KnowledgeBaseRetrievalTool;
import cn.tolim.pudans.core.ai.rag.RagContext;
import cn.tolim.pudans.core.ai.rag.RetrievalOptions;
import cn.tolim.pudans.core.ai.team.AiTeamService;
import cn.tolim.pudans.core.storage.StorageService;
import cn.tolim.pudans.extension.exception.ExtensionNotFoundException;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository.Conversation;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository.NewConversation;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiMessageRepository.Message;
import cn.tolim.pudans.core.ai.chat.AiChatToolService.ToolOutcome;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.query.Query;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    private static final String AGENT_SOURCE = "pudans-chat";

    private static final String CODE_MODE_NOT_FOUND = "ai-mode-not-found";

    private static final String CODE_KB_FORBIDDEN = "ai-kb-forbidden";

    private static final String CODE_TEAM_FORBIDDEN = "ai-team-forbidden";

    private static final String CODE_REQUEST_INVALID = "ai-chat-request-invalid";

    private static final String CODE_MESSAGES_INVALID = "ai-chat-messages-invalid";

    private static final int DEFAULT_HISTORY_LIMIT = 200;

    private static final int TITLE_LIMIT = 80;

    private static final int MAX_ATTACHMENT_BYTES = 25 * 1024 * 1024;

    private static final int MAX_INLINE_TEXT_BYTES = 1024 * 1024;

    private final AiSettingService settingService;

    private final AiChatModeService chatModeService;

    private final AiTeamService teamService;

    private final AiConversationService conversationService;

    private final ReactiveExtensionClient client;

    private final KnowledgeBaseAcl knowledgeBaseAcl;

    private final KnowledgeBaseRetrievalService retrievalService;

    private final AiEmbeddingService embeddingService;

    private final AiMemoryContextBuilder memoryContextBuilder;

    private final AiGatewayService gatewayService;

    private final MemoryTaskScheduler memoryTaskScheduler;

    private final StorageService storageService;

    private final AiAgentContextService agentContextService;

    private final AiChatToolService toolService;

    private final SlashCommandService slashCommandService;

    private final HookService hookService;

    private final ThreadJournalStore journalStore;

    private final AgentStateStore stateStore;

    private final ConcurrentMap<String, ChatRun> activeRuns = new ConcurrentHashMap<>();

    public Mono<AiChatCompletionResult> complete(AiChatCommand command) {
        return prepare(command)
                .flatMap(prepared -> completeWithHarness(prepared)
                        .flatMap(round -> appendAssistant(
                                        prepared.conversation(),
                                        prepared.turnIndex(),
                                        round.content(),
                                        prepared.requestId(),
                                        round.model(),
                                        round.usage() == null ? null : round.usage().outputTokens(),
                                        round.metadata())
                                .flatMap(message -> conversationService
                                        .markPending(prepared.conversation(), 2)
                                        .then(enqueueExtraction(prepared, message))
                                        .then(dispatchSessionEnd(prepared))
                                        .thenReturn(new AiChatCompletionResult(
                                                prepared.conversation().id(),
                                                String.valueOf(message.id()),
                                                round.content(),
                                                round.model(),
                                                round.provider(),
                                                round.account(),
                                                round.usage(),
                                                round.points(),
                                                prepared.citations())))));
    }

    private Mono<AgentRound> completeWithHarness(Prepared prepared) {
        var currentUser = lastUserMessage(prepared.messages());
        if (currentUser == null) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, CODE_MESSAGES_INVALID, "缺少本轮用户消息。"));
        }
        var outcomes = new CopyOnWriteArrayList<ToolOutcome>();
        var turn = new TurnCollector(false);
        return retrieveKnowledge(prepared, userText(currentUser))
                .flatMap(knowledge -> sessionStore(prepared).flatMap(store -> {
                    var state = new GatewayRunState();
                    var agent = harnessAgent(prepared, store, state, outcomes);
                    var run = agent.start(
                            userMessage(currentUser, knowledge),
                            runtimeContext(prepared),
                            false);
                    activeRuns.put(run.id(), new ChatRun(prepared.userId(), run));
                    run.onEvent(turn);
                    return run.result()
                            .doFinally(signal -> activeRuns.remove(run.id()))
                            .map(result -> agentRound(result, state, outcomes, turn));
                }));
    }

    public boolean confirm(String userId, String runId, String toolCallId, boolean approved) {
        var chatRun = activeRuns.get(runId);
        if (chatRun == null || !chatRun.userId().equals(userId)) {
            return false;
        }
        return chatRun.run().confirm(toolCallId, approved);
    }

    private static @Nullable AiChatMessage lastUserMessage(List<AiChatMessage> messages) {
        for (var index = messages.size() - 1; index >= 0; index--) {
            var message = messages.get(index);
            if ("user".equals(message.role())) {
                return message;
            }
        }
        return null;
    }

    private static String systemText(List<AiChatMessage> messages) {
        var sections = new ArrayList<String>();
        for (var message : messages) {
            if (!"system".equals(message.role())) {
                continue;
            }
            var text = message.parts().stream()
                    .filter(AiContentPart.Text.class::isInstance)
                    .map(AiContentPart.Text.class::cast)
                    .map(AiContentPart.Text::text)
                    .collect(Collectors.joining());
            if (StringUtils.hasText(text)) {
                sections.add(text);
            }
        }
        return String.join("\n\n", sections);
    }

    private static List<ChatMessage> historyMessages(
            List<AiChatMessage> messages, AiChatMessage currentUser) {
        var history = new ArrayList<ChatMessage>();
        for (var message : messages) {
            if (message == currentUser || "system".equals(message.role())) {
                continue;
            }
            history.add(GatewayChatMessageMapper.toChatMessage(message));
        }
        return history;
    }

    private static String userText(AiChatMessage message) {
        var text = new StringBuilder();
        for (var part : message.parts()) {
            if (part instanceof AiContentPart.Text content) {
                text.append(content.text());
            }
        }
        return text.toString();
    }

    private static UserMessage userMessage(AiChatMessage currentUser, String knowledge) {
        var contents = new ArrayList<Content>();
        if (StringUtils.hasText(knowledge)) {
            contents.add(TextContent.from(knowledge));
        }
        contents.addAll(GatewayChatMessageMapper.toContents(currentUser));
        return UserMessage.from(contents);
    }

    private Mono<AgentStateStore> sessionStore(Prepared prepared) {
        var sessionId = prepared.conversation().sessionKey();
        var history = historyMessages(prepared.messages(), lastUserMessage(prepared.messages()));
        return stateStore
                .load(prepared.userId(), sessionId)
                .flatMap(existing -> {
                    if (existing.messages().isEmpty()) {
                        return stateStore
                                .save(
                                        prepared.userId(),
                                        sessionId,
                                        new AgentState(history, existing.summary(), existing.attributes()))
                                .thenReturn(stateStore);
                    }
                    if (prepared.regenerate()) {
                        var trimmed = existing.copy();
                        trimLastTurn(trimmed);
                        return stateStore
                                .save(prepared.userId(), sessionId, trimmed)
                                .thenReturn(stateStore);
                    }
                    return Mono.just(stateStore);
                })
                .switchIfEmpty(Mono.defer(() -> stateStore
                        .save(prepared.userId(), sessionId, new AgentState(history, "", Map.of()))
                        .thenReturn(stateStore)));
    }

    static void trimLastTurn(AgentState state) {
        var messages = new ArrayList<>(state.messages());
        var lastUser = -1;
        for (var index = messages.size() - 1; index >= 0; index--) {
            if (messages.get(index) instanceof UserMessage) {
                lastUser = index;
                break;
            }
        }
        if (lastUser < 0) {
            return;
        }
        state.replaceMessages(messages.subList(0, lastUser));
    }

    private static RuntimeContext runtimeContext(Prepared prepared) {
        return RuntimeContext.of(prepared.userId(), prepared.conversation().sessionKey());
    }

    private Mono<String> retrieveKnowledge(Prepared prepared, String query) {
        if (prepared.retriever() == null) {
            return Mono.just("");
        }
        return Mono.fromCallable(() -> prepared.retriever()
                        .retrieve(Query.from(query))
                        .stream()
                        .map(content -> content.textSegment().text())
                        .collect(Collectors.joining("\n\n")))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private HarnessAgent harnessAgent(
            Prepared prepared,
            AgentStateStore store,
            GatewayRunState state,
            CopyOnWriteArrayList<ToolOutcome> outcomes) {
        var context = new GatewayChatContext(
                prepared.userId(),
                AGENT_SOURCE,
                prepared.conversation().sessionKey(),
                null,
                prepared.requestId(),
                prepared.idempotencyKey(),
                prepared.modeCode(),
                prepared.routeGroup(),
                prepared.agentName(),
                prepared.conversation().id(),
                prepared.modelOverride());
        var params = prepared.params();
        return HarnessAgent.builder()
                .name(prepared.agentName())
                .systemPrompt(systemText(prepared.messages()))
                .chatModel(new GatewayChatModel(gatewayService, context, state))
                .streamingChatModel(new GatewayStreamingChatModel(gatewayService, context, state))
                .toolkit(toolkit(prepared, outcomes))
                .stateStore(store)
                .journalStore(journalStore)
                .permissionEngine(new PermissionEngine(prepared.harness().permission()))
                .confirmationTimeout(prepared.harness().confirmationTimeout())
                .knowledgeBaseRetrievalTool(prepared.knowledgeTool())
                .planModeEnabled(prepared.harness().planModeEnabled())
                .todoEnabled(prepared.harness().todosEnabled())
                .compaction(prepared.harness().compaction())
                .toolResultEviction(prepared.harness().eviction())
                .temperature(params.temperature())
                .maxOutputTokens(params.maxTokens())
                .topP(params.topP())
                .topK(params.topK())
                .stopSequences(params.stop())
                .build();
    }

    private HarnessToolkit toolkit(Prepared prepared, CopyOnWriteArrayList<ToolOutcome> outcomes) {
        var toolkit = new HarnessToolkit();
        for (var tool : prepared.tools()) {
            toolkit.register(tool, (request, arguments) -> {
                var outcome = executeTool(prepared, request, outcomes);
                return new HarnessToolkit.ToolResult(outcome.output(), outcome.isError());
            });
        }
        // 模型可能调用未声明的工具，按用户权限执行，保持与旧版一致的容错行为。
        toolkit.fallbackHandler((request, arguments) -> {
            var outcome = executeTool(prepared, request, outcomes);
            return new HarnessToolkit.ToolResult(outcome.output(), outcome.isError());
        });
        return toolkit;
    }

    private ToolOutcome executeTool(
            Prepared prepared, ToolExecutionRequest request, CopyOnWriteArrayList<ToolOutcome> outcomes) {
        var call = new AiContentPart.ToolCall(
                request.id(),
                request.name(),
                LangChain4jToolMapper.arguments(request.arguments()),
                null);
        var outcome = toolService.execute(prepared.userId(), prepared.agent(), call).block();
        if (outcome == null) {
            throw new IllegalStateException("工具执行返回空结果。");
        }
        outcomes.add(outcome);
        return outcome;
    }

    private static AgentRound agentRound(
            AgentResult result,
            GatewayRunState state,
            List<ToolOutcome> outcomes,
            TurnCollector turn) {
        var meta = state.meta();
        return new AgentRound(
                turn.content(result.content()),
                meta == null ? null : meta.model(),
                meta == null ? null : meta.provider(),
                meta == null ? null : meta.account(),
                state.usage(),
                state.points(),
                roundMetadata(turn.thinking(), outcomes));
    }

    public Flux<AiChatStreamEvent> stream(AiChatCommand command) {
        return Flux.defer(() -> prepare(command).flatMapMany(this::streamWithHarness));
    }

    private Flux<AiChatStreamEvent> streamWithHarness(Prepared prepared) {
        var currentUser = lastUserMessage(prepared.messages());
        if (currentUser == null) {
            return Flux.error(new ApiException(
                    HttpStatus.BAD_REQUEST, CODE_MESSAGES_INVALID, "缺少本轮用户消息。"));
        }
        var outcomes = new CopyOnWriteArrayList<ToolOutcome>();
        return retrieveKnowledge(prepared, userText(currentUser))
                .flatMapMany(knowledge -> Flux.defer(() -> sessionStore(prepared)
                        .flatMapMany(store -> {
                            var state = new GatewayRunState();
                            var turn = new TurnCollector(true);
                            var agent = harnessAgent(prepared, store, state, outcomes);
                            var run = agent.start(
                                    userMessage(currentUser, knowledge),
                                    runtimeContext(prepared),
                                    true);
                            activeRuns.put(run.id(), new ChatRun(prepared.userId(), run));
                            return streamRun(prepared, run, state, outcomes, turn)
                                    .onErrorResume(error -> Flux.just(new AiChatStreamEvent.Error(
                                            "ai-chat-stream-failed",
                                            errorMessage(error),
                                            false,
                                            !turn.content("").isEmpty())));
                        })))
                .onErrorResume(error -> Flux.just(new AiChatStreamEvent.Error(
                        "ai-chat-stream-failed", errorMessage(error), false, false)));
    }

    private Flux<AiChatStreamEvent> streamRun(
            Prepared prepared,
            AgentRun run,
            GatewayRunState state,
            List<ToolOutcome> outcomes,
            TurnCollector turn) {
        return Flux.create(sink -> {
            var metaEmitted = new AtomicBoolean();
            run.onEvent(event -> {
                var mapped = mapHarnessEvent(event, outcomes);
                if (mapped.isEmpty()) {
                    return;
                }
                emitMeta(sink, prepared, state, metaEmitted);
                mapped.forEach(sink::next);
            });
            sink.onCancel(run::cancel);
            var execution = run.result().doFinally(signal -> activeRuns.remove(run.id()));
            execution.subscribe(
                    result -> persistStreamResult(prepared, state, outcomes, turn, result)
                            .subscribe(message -> {
                                emitMeta(sink, prepared, state, metaEmitted);
                                if (prepared.retriever() == null && prepared.knowledgeTool() != null) {
                                    prepared.citations().forEach(citation ->
                                            sink.next(new AiChatStreamEvent.CitationEvent(citation)));
                                }
                                var usage = state.usage();
                                var hasUsage = usage != null
                                        && (usage.inputTokens() != null
                                                || usage.outputTokens() != null
                                                || usage.cacheReadTokens() != null
                                                || usage.cacheWriteTokens() != null);
                                if (hasUsage || state.points().signum() > 0) {
                                    sink.next(new AiChatStreamEvent.Usage(
                                            usage, state.points(), state.billingSource()));
                                }
                                sink.next(new AiChatStreamEvent.Done(
                                        String.valueOf(message.id()),
                                        finishReason(result.response()),
                                        false));
                                sink.complete();
                            }, sink::error),
                    sink::error);
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    private Mono<Message> persistStreamResult(
            Prepared prepared,
            GatewayRunState state,
            List<ToolOutcome> outcomes,
            TurnCollector turn,
            AgentResult result) {
        var usage = state.usage();
        return appendAssistant(
                        prepared.conversation(),
                        prepared.turnIndex(),
                        turn.content(result.content()),
                        prepared.requestId(),
                        modelName(result, state),
                        usage == null ? null : usage.outputTokens(),
                        roundMetadata(turn.thinking(), outcomes))
                .flatMap(message -> conversationService
                        .markPending(prepared.conversation(), 2)
                        .then(enqueueExtraction(prepared, message))
                        .then(dispatchSessionEnd(prepared))
                        .thenReturn(message));
    }

    private static @Nullable String modelName(AgentResult result, GatewayRunState state) {
        if (result.response() != null && StringUtils.hasText(result.response().modelName())) {
            return result.response().modelName();
        }
        var meta = state.meta();
        return meta == null ? null : meta.model();
    }

    private void emitMeta(
            FluxSink<AiChatStreamEvent> sink,
            Prepared prepared,
            GatewayRunState state,
            AtomicBoolean metaEmitted) {
        if (!metaEmitted.compareAndSet(false, true)) {
            return;
        }
        var meta = state.meta() == null ? fallbackMeta(prepared) : state.meta();
        sink.next(new AiChatStreamEvent.Meta(streamMeta(meta)));
        prepared.citations().forEach(citation -> sink.next(new AiChatStreamEvent.CitationEvent(citation)));
    }

    private static List<AiChatStreamEvent> mapHarnessEvent(
            AgentEvent event, List<ToolOutcome> outcomes) {
        if (event instanceof AgentEvent.TextBlockDelta delta) {
            return List.of(new AiChatStreamEvent.Delta(delta.delta(), null));
        }
        if (event instanceof AgentEvent.ThinkingBlockDelta delta) {
            return List.of(new AiChatStreamEvent.Delta(null, delta.delta()));
        }
        if (event instanceof AgentEvent.RequireUserConfirm confirm) {
            return List.of(new AiChatStreamEvent.ToolConfirm(
                    confirm.runId(), confirm.id(), confirm.name(), confirm.message()));
        }
        if (event instanceof AgentEvent.ToolCallStart start) {
            return List.of(
                    new AiChatStreamEvent.Stage("tool", "正在执行工具调用。"),
                    new AiChatStreamEvent.ToolCall(
                            start.id(),
                            start.name(),
                            serverOf(start.name()),
                            LangChain4jToolMapper.arguments(start.arguments())));
        }
        if (event instanceof AgentEvent.ToolResultEnd end) {
            var outcome = outcomes.stream()
                    .filter(item -> item.id().equals(end.id()))
                    .reduce((first, second) -> second)
                    .orElse(null);
            return List.of(new AiChatStreamEvent.ToolResult(
                    end.id(),
                    end.name(),
                    outcome == null ? end.output() : outcome.output(),
                    end.error(),
                    outcome == null ? 0 : outcome.durationMs()));
        }
        return List.of();
    }

    private static AiGatewayMeta fallbackMeta(Prepared prepared) {
        return new AiGatewayMeta(
                prepared.requestId(),
                prepared.conversation().id(),
                prepared.modeCode(),
                prepared.routeGroup(),
                null,
                null,
                prepared.modelOverride(),
                null,
                null,
                prepared.agentName(),
                prepared.conversation().sessionKey());
    }

    private static String finishReason(dev.langchain4j.model.chat.response.ChatResponse response) {
        if (response.finishReason() == null) {
            return "stop";
        }
        return switch (response.finishReason()) {
            case STOP -> "stop";
            case LENGTH -> "length";
            case TOOL_EXECUTION -> "tool_calls";
            case CONTENT_FILTER -> "content_filter";
            case OTHER -> "stop";
        };
    }

    private Mono<Prepared> prepare(AiChatCommand command) {
        return settingService.spec().flatMap(setting -> resolveMode(command.request().mode(), setting)
                .flatMap(mode -> resolveConversation(command, mode, setting)
                        .flatMap(conversationState -> resolveUserContent(command, conversationState)
                                .flatMap(userContent -> dispatchHook(
                                                conversationState.agent().getMetadata().getName(),
                                                AiHook.Event.USER_PROMPT_SUBMIT,
                                                Map.of("prompt", userContent))
                                        .then(agentContextService.build(conversationState.agent()))
                                        .flatMap(context -> dispatchSessionStart(
                                                        conversationState.agent().getMetadata().getName(),
                                                        conversationState.conversation().id())
                                                .then(ragContext(command, mode, setting))
                                                .flatMap(rag -> memory(
                                                                command, conversationState, setting, userContent)
                                                        .flatMap(memory -> attachments(command)
                                                                .flatMap(attachmentParts -> buildPrepared(
                                                                        command,
                                                                        setting,
                                                                        mode,
                                                                        conversationState,
                                                                        rag,
                                                                        memory,
                                                                        context,
                                                                        userContent,
                                                                        attachmentParts)))))))));
    }

    private Mono<String> resolveUserContent(AiChatCommand command, ConversationState state) {
        var request = command.request();
        if (!StringUtils.hasText(request.command())) {
            return Mono.just(lastUserContent(request));
        }
        var agentName = state.agent().getMetadata().getName();
        var input = StringUtils.hasText(request.commandInput()) ? " " + request.commandInput() : "";
        return slashCommandService
                .resolve(agentName, "/" + request.command() + input)
                .map(SlashCommandService.ResolvedSlashCommand::expandedPrompt);
    }

    private Mono<List<AiContentPart>> attachments(AiChatCommand command) {
        var attachments = command.request().attachments();
        if (attachments == null || attachments.isEmpty()) {
            return Mono.just(List.of());
        }
        return Flux.fromIterable(attachments)
                .concatMap(attachment -> attachmentPart(command.userId(), attachment))
                .collectList();
    }

    Mono<AiContentPart> attachmentPart(
            String userId, AiChatCompletionRequest.AiChatAttachment attachment) {
        return storageService
                .getFile(attachment.storageFileId())
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.BAD_REQUEST, CODE_REQUEST_INVALID, "附件不存在。")))
                .filter(file -> userId.equals(file.getSpec().getOwner()))
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.FORBIDDEN, CODE_REQUEST_INVALID, "无权访问附件。")))
                .filter(file -> file.getSpec().getState() == StorageFileState.ACTIVE)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.BAD_REQUEST, CODE_REQUEST_INVALID, "附件不可用。")))
                .flatMap(file -> {
                    var contentType = StringUtils.hasText(file.getSpec().getContentType())
                            ? file.getSpec().getContentType()
                            : attachment.contentType();
                    if (!StringUtils.hasText(contentType)) {
                        contentType = "application/octet-stream";
                    }
                    if (isUnsafeAttachment(contentType)) {
                        return Mono.error(new ApiException(
                                HttpStatus.BAD_REQUEST, CODE_REQUEST_INVALID, "不支持该类型的附件。"));
                    }
                    var mediaType = contentType;
                    var fileName = StringUtils.hasText(file.getSpec().getOriginalName())
                            ? file.getSpec().getOriginalName()
                            : attachment.name();
                    var data = storageService.readBytes(attachment.storageFileId(), MAX_ATTACHMENT_BYTES);
                    if (mediaType.startsWith("image/")) {
                        return data.map(bytes -> new AiContentPart.Image(
                                mediaType,
                                Base64.getEncoder().encodeToString(bytes),
                                null,
                                "auto",
                                null));
                    }
                    if (isInlineText(mediaType)) {
                        return data.map(bytes -> {
                            if (bytes.length <= MAX_INLINE_TEXT_BYTES) {
                                return new AiContentPart.Text(textAttachment(fileName, bytes), null);
                            }
                            return filePart(mediaType, fileName, bytes);
                        });
                    }
                    return data.map(bytes -> filePart(mediaType, fileName, bytes));
                });
    }

    private static boolean isInlineText(String contentType) {
        var type = contentType.toLowerCase();
        return type.startsWith("text/")
                || type.equals("application/json")
                || type.equals("application/xml")
                || type.equals("application/yaml")
                || type.equals("application/x-yaml");
    }

    private static AiContentPart filePart(String mediaType, @Nullable String fileName, byte[] bytes) {
        return new AiContentPart.File(
                mediaType, Base64.getEncoder().encodeToString(bytes), null, fileName, null, null);
    }

    private static String textAttachment(@Nullable String fileName, byte[] bytes) {
        var name = StringUtils.hasText(fileName) ? fileName : "附件";
        return "附件《" + name + "》内容：\n\n" + DocumentParser.decodeUtf8(bytes);
    }

    private static boolean isUnsafeAttachment(String contentType) {
        var type = contentType.toLowerCase();
        return type.equals("text/html")
                || type.equals("application/xhtml+xml")
                || type.equals("image/svg+xml")
                || type.equals("text/javascript")
                || type.equals("application/javascript");
    }

    private Mono<AiChatMode> resolveMode(String modeCode, AiSetting.Spec setting) {
        var name = StringUtils.hasText(modeCode) ? modeCode : setting.getDefaultChatMode();
        if (!StringUtils.hasText(name)) {
            return Mono.error(new ApiException(HttpStatus.NOT_FOUND, CODE_MODE_NOT_FOUND, "未配置对话模式。"));
        }
        return chatModeService
                .get(name)
                .onErrorMap(
                        ExtensionNotFoundException.class,
                        error -> new ApiException(
                                HttpStatus.NOT_FOUND, CODE_MODE_NOT_FOUND, "对话模式不存在。"))
                .filter(mode -> mode.getSpec() != null && mode.getSpec().isEnabled())
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, CODE_MODE_NOT_FOUND, "对话模式不存在或已禁用。")));
    }

    private Mono<ConversationState> resolveConversation(
            AiChatCommand command, AiChatMode mode, AiSetting.Spec setting) {
        if (command.request().conversationId() != null) {
            return conversationService
                    .requireOwned(command.request().conversationId(), command.userId())
                    .flatMap(conversation -> ensureConversationScope(command, conversation)
                            .flatMap(agent -> (command.request().isRegenerate()
                                            ? conversationService.deleteLastAssistant(conversation)
                                            : Mono.<Void>empty())
                                    .then(history(conversation, setting))
                                    .map(history -> new ConversationState(conversation, agent, history))));
        }
        if (command.request().isRegenerate()) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, CODE_REQUEST_INVALID, "重试需要指定已有的会话。"));
        }
        return resolveAgent(command.request().agentId(), command.request().teamId(), setting, command.userId())
                .flatMap(agent -> conversationService
                        .createOrGet(new NewConversation(
                                AiGatewaySessionService.DEFAULT_SPACE,
                                command.userId(),
                                agent.getSpec().getTeam(),
                                agent.getMetadata().getName(),
                                AGENT_SOURCE,
                                "chat-" + UUID.randomUUID(),
                                null,
                                title(command.request()),
                                mode.getMetadata().getName(),
                                command.request().routeGroup()))
                        .map(conversation -> new ConversationState(conversation, agent, List.of())));
    }

    private Mono<AiAgent> ensureConversationScope(AiChatCommand command, Conversation conversation) {
        if (StringUtils.hasText(command.request().agentId())
                && !command.request().agentId().equals(conversation.agentId())) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, CODE_REQUEST_INVALID, "会话绑定的 Agent 与请求不一致。"));
        }
        if (StringUtils.hasText(command.request().teamId())
                && !command.request().teamId().equals(conversation.teamId())) {
            return Mono.error(new ApiException(HttpStatus.FORBIDDEN, CODE_TEAM_FORBIDDEN, "无权访问该团队。"));
        }
        return teamService.requireAgent(conversation.agentId(), command.userId());
    }

    private Mono<AiAgent> resolveAgent(
            String requestedAgent, String requestedTeam, AiSetting.Spec setting, String userId) {
        var defaults = setting.getDefaults() == null ? new AiSetting.Defaults() : setting.getDefaults();
        var agentName = StringUtils.hasText(requestedAgent)
                ? requestedAgent
                : StringUtils.hasText(defaults.getAgent()) ? defaults.getAgent() : AiTeamService.DEFAULT_AGENT;
        return teamService.requireAgent(agentName, userId).flatMap(agent -> {
            if (StringUtils.hasText(requestedTeam) && !requestedTeam.equals(agent.getSpec().getTeam())) {
                return Mono.error(new ApiException(
                        HttpStatus.FORBIDDEN, CODE_TEAM_FORBIDDEN, "无权访问该团队。"));
            }
            return Mono.just(agent);
        });
    }

    private Mono<List<Message>> history(Conversation conversation, AiSetting.Spec setting) {
        var prompt = setting.getPrompt() == null ? new AiSetting.Prompt() : setting.getPrompt();
        var limit = Math.max(2, Math.min(DEFAULT_HISTORY_LIMIT, prompt.getHistoryWindowTurns() * 2));
        return conversationService.recentHistory(conversation, limit).collectList();
    }

    private Mono<RagContext> ragContext(
            AiChatCommand command, AiChatMode mode, AiSetting.Spec setting) {
        var codes = knowledgeBaseCodes(command, mode, setting);
        if (codes.isEmpty()) {
            return Mono.just(new RagContext(
                    command.userId(),
                    null,
                    List.of(),
                    command.roleNames(),
                    command.deptId(),
                    options(setting),
                    candidateLimit(setting)));
        }
        return Flux.fromIterable(codes)
                .concatMap(code -> client.get(KnowledgeBase.class, code)
                        .onErrorMap(
                                ExtensionNotFoundException.class,
                                error -> new ApiException(
                                        HttpStatus.NOT_FOUND, CODE_KB_FORBIDDEN, "知识库不存在：" + code))
                        .flatMap(knowledgeBase -> {
                            if (!knowledgeBaseAcl.canRead(
                                    knowledgeBase, command.userId(), command.roleNames(), command.deptId())) {
                                return Mono.error(new ApiException(
                                        HttpStatus.FORBIDDEN, CODE_KB_FORBIDDEN, "无权访问知识库：" + code));
                            }
                            return Mono.just(knowledgeBase);
                        }))
                .collectList()
                .map(knowledgeBases -> new RagContext(
                        command.userId(),
                        null,
                        knowledgeBases,
                        command.roleNames(),
                        command.deptId(),
                        options(setting),
                        candidateLimit(setting)));
    }

    private Mono<MemoryInjection> memory(
            AiChatCommand command, ConversationState state, AiSetting.Spec setting, String query) {
        var retrieval = setting.getRetrieval() == null ? new AiSetting.Retrieval() : setting.getRetrieval();
        return memoryContextBuilder
                .build(
                        new MemoryContext(
                                state.conversation().teamId(),
                                command.userId(),
                                state.conversation().agentId(),
                                state.conversation().sessionKey(),
                                null),
                        query,
                        Math.max(1, retrieval.getL1Limit()),
                        Math.max(1, retrieval.getL2Limit()))
                .defaultIfEmpty(MemoryInjection.empty());
    }

    private Mono<Prepared> buildPrepared(
            AiChatCommand command,
            AiSetting.Spec setting,
            AiChatMode mode,
            ConversationState state,
            RagContext rag,
            MemoryInjection memory,
            AiAgentContextService.AgentContext context,
            String userContent,
            List<AiContentPart> attachmentParts) {
        var prompt = setting.getPrompt() == null ? new AiSetting.Prompt() : setting.getPrompt();
        var retrievalSetting = setting.getRetrieval() == null
                ? new AiSetting.Retrieval()
                : setting.getRetrieval();
        var memorySetting = setting.getMemory() == null ? new AiSetting.Memory() : setting.getMemory();
        var maxOutput = command.request().maxOutputTokens() != null
                ? command.request().maxOutputTokens()
                : prompt.getMaxOutputTokens();
        var requestId = "req-" + UUID.randomUUID();
        var idempotencyKey = StringUtils.hasText(command.request().idempotencyKey())
                ? command.request().idempotencyKey()
                : requestId;
        if (command.request().isRegenerate() && state.history().isEmpty()) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, CODE_REQUEST_INVALID, "没有可重试的用户消息。"));
        }
        var turnIndex = command.request().isRegenerate()
                ? state.history().getLast().turnIndex()
                : state.history().isEmpty() ? 1 : state.history().getLast().turnIndex() + 1;
        var system = systemContent(state.agent().getSpec().getPrompt(), memory);
        if (StringUtils.hasText(context.systemAdditions())) {
            system = StringUtils.hasText(system)
                    ? system + "\n\n" + context.systemAdditions()
                    : context.systemAdditions();
        }
        var messages = new ArrayList<AiChatMessage>();
        if (StringUtils.hasText(system)) {
            messages.add(new AiChatMessage("system", List.of(new AiContentPart.Text(system, null))));
        }
        for (var message : state.history()) {
            if (StringUtils.hasText(message.content())
                    && ("user".equals(message.role()) || "assistant".equals(message.role()))) {
                messages.add(new AiChatMessage(
                        message.role(), List.of(new AiContentPart.Text(message.content(), null))));
            }
        }
        if (!command.request().isRegenerate()) {
            var userParts = new ArrayList<AiContentPart>();
            userParts.add(new AiContentPart.Text(userContent, null));
            userParts.addAll(attachmentParts);
            messages.add(new AiChatMessage("user", userParts));
        }
        var params = new AiRequestParams(
                command.request().model(),
                command.request().temperature(),
                maxOutput,
                command.request().topP(),
                null,
                null,
                null,
                Map.of());
        var agenticRetrieval = retrievalSetting.isAgenticEnabled() && !rag.isEmpty();
        var retriever = rag.isEmpty() || agenticRetrieval
                ? null
                : new KnowledgeBaseContentRetriever(retrievalService, embeddingService, rag);
        var knowledgeTool = agenticRetrieval
                ? new KnowledgeBaseRetrievalTool(retrievalService, embeddingService, rag)
                : null;
        var prepared = new Prepared(
                command.userId(),
                state.agent(),
                state.conversation(),
                turnIndex,
                retriever,
                knowledgeTool,
                requestId,
                idempotencyKey,
                memorySetting,
                List.copyOf(messages),
                context.tools(),
                params,
                mode.getMetadata().getName(),
                command.request().routeGroup(),
                command.request().model(),
                command.request().isRegenerate(),
                context.harness());
        var appendUser = command.request().isRegenerate()
                ? Mono.<Message>empty()
                : conversationService.appendUserMessage(
                        state.conversation(),
                        turnIndex,
                        0,
                        userContent,
                        requestId,
                        attachmentMetadata(command.request().attachments()));
        return appendUser.thenReturn(prepared);
    }

    private static JsonNode attachmentMetadata(List<AiChatCompletionRequest.AiChatAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return null;
        }
        var root = JsonUtils.mapper().createObjectNode();
        var array = root.putArray("attachments");
        for (var attachment : attachments) {
            var node = array.addObject();
            node.put("fileId", attachment.storageFileId());
            node.put("type", attachment.type());
            if (StringUtils.hasText(attachment.name())) {
                node.put("name", attachment.name());
            }
            if (StringUtils.hasText(attachment.contentType())) {
                node.put("contentType", attachment.contentType());
            }
            if (attachment.size() != null) {
                node.put("size", attachment.size());
            }
        }
        return root;
    }

    private Mono<Void> enqueueExtraction(Prepared prepared, Message message) {
        if (!prepared.memorySetting().isExtractionEnabled()) {
            return Mono.empty();
        }
        return memoryTaskScheduler
                .enqueueExtraction(prepared.conversation(), prepared.conversation().userId(), message.id())
                .onErrorResume(error -> {
                    log.warn("L1 记忆任务投递失败：会话 {}。", prepared.conversation().id(), error);
                    return Mono.empty();
                });
    }
    private static @Nullable JsonNode roundMetadata(
            @Nullable String reasoning, List<ToolOutcome> exchanges) {
        var root = JsonUtils.mapper().createObjectNode();
        if (StringUtils.hasText(reasoning)) {
            root.put("reasoning", reasoning);
        }
        if (!exchanges.isEmpty()) {
            var calls = root.putArray("toolCalls");
            var results = root.putArray("toolResults");
            for (var outcome : exchanges) {
                var call = calls.addObject();
                call.put("id", outcome.id());
                call.put("name", outcome.name());
                if (StringUtils.hasText(outcome.server())) {
                    call.put("server", outcome.server());
                }
                if (outcome.arguments() != null) {
                    call.set("arguments", outcome.arguments());
                }
                var result = results.addObject();
                result.put("id", outcome.id());
                result.put("name", outcome.name());
                result.put("output", outcome.output());
                result.put("isError", outcome.isError());
                result.put("durationMs", outcome.durationMs());
            }
        }
        return root.isEmpty() ? null : root;
    }

    private static @Nullable String serverOf(String toolName) {
        if (toolName == null || !toolName.startsWith("mcp.")) {
            return null;
        }
        var rest = toolName.substring("mcp.".length());
        var separator = rest.indexOf('.');
        return separator > 0 ? rest.substring(0, separator) : null;
    }

    private Mono<Void> dispatchSessionStart(String agentName, long conversationId) {
        return dispatchHook(agentName, AiHook.Event.SESSION_START, Map.of("conversationId", conversationId));
    }

    private Mono<Void> dispatchSessionEnd(Prepared prepared) {
        return dispatchHook(
                prepared.agentName(),
                AiHook.Event.SESSION_END,
                Map.of("conversationId", prepared.conversation().id()));
    }

    private Mono<Void> dispatchHook(String agentName, AiHook.Event event, Map<String, Object> payload) {
        return hookService.dispatch(agentName, event, payload).onErrorResume(error -> {
            log.warn("Hook 事件发布失败：Agent {}，事件 {}。", agentName, event, error);
            return Mono.empty();
        });
    }

    private record ChatRun(String userId, AgentRun run) {}

    private record AgentRound(
            String content,
            @Nullable String model,
            @Nullable String provider,
            @Nullable String account,
            @Nullable AiTokenUsage usage,
            BigDecimal points,
            @Nullable JsonNode metadata) {}

    private static final class TurnCollector implements Consumer<AgentEvent> {

        private final boolean streaming;

        private final StringBuilder content = new StringBuilder();

        private String thinking = "";

        private TurnCollector(boolean streaming) {
            this.streaming = streaming;
        }

        @Override
        public void accept(AgentEvent event) {
            if (streaming && event instanceof AgentEvent.TextBlockStart) {
                if (!content.isEmpty()) {
                    content.append("\n\n");
                }
                return;
            }
            if (streaming && event instanceof AgentEvent.TextBlockDelta delta) {
                content.append(delta.delta());
                return;
            }
            if (!streaming && event instanceof AgentEvent.TextBlockEnd end) {
                if (!content.isEmpty()) {
                    content.append("\n\n");
                }
                content.append(end.text());
                return;
            }
            if (event instanceof AgentEvent.ThinkingBlockEnd end) {
                thinking = end.text();
            }
        }

        private String content(String fallback) {
            if (!content.isEmpty()) {
                return content.toString();
            }
            return fallback == null ? "" : fallback;
        }

        private String thinking() {
            return thinking;
        }
    }

    private static String errorMessage(Throwable error) {
        var message = error.getMessage();
        return StringUtils.hasText(message) ? "AI 对话失败：" + message : "AI 对话失败。";
    }

    private Mono<Message> appendAssistant(
            Conversation conversation,
            int turnIndex,
            String content,
            String requestId,
            String modelCode,
            Integer outputTokens,
            @Nullable JsonNode metadata) {
        return conversationService.appendAssistantMessage(
                conversation, turnIndex, 1, content, requestId, modelCode, outputTokens, metadata);
    }

    private static AiChatCompletionResult.StreamMeta streamMeta(cn.tolim.pudans.core.ai.gateway.AiGatewayMeta meta) {
        return new AiChatCompletionResult.StreamMeta(
                meta.requestId(),
                meta.conversationId(),
                meta.mode(),
                meta.routeGroup(),
                meta.provider(),
                meta.account(),
                meta.model(),
                meta.tier() == null ? null : meta.tier().name(),
                meta.teamId(),
                meta.agentId(),
                meta.sessionKey());
    }

    private static String lastUserContent(AiChatCompletionRequest request) {
        for (int i = request.messages().size() - 1; i >= 0; i--) {
            var message = request.messages().get(i);
            if ("user".equals(message.role()) && StringUtils.hasText(message.content())) {
                return message.content();
            }
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, CODE_MESSAGES_INVALID, "缺少本轮用户消息。");
    }

    private static String title(AiChatCompletionRequest request) {
        var content = lastUserContent(request);
        return content.length() <= TITLE_LIMIT ? content : content.substring(0, TITLE_LIMIT);
    }

    private static List<String> knowledgeBaseCodes(
            AiChatCommand command, AiChatMode mode, AiSetting.Spec setting) {
        var explicit = command.request().kbCodes();
        if (explicit != null && !explicit.isEmpty()) {
            return explicit.stream().filter(StringUtils::hasText).distinct().toList();
        }
        if (mode.getSpec() != null && mode.getSpec().getKnowledgeBaseCodes() != null) {
            var modeCodes = mode.getSpec().getKnowledgeBaseCodes().stream()
                    .filter(StringUtils::hasText)
                    .toList();
            if (!modeCodes.isEmpty()) {
                return modeCodes;
            }
        }
        var defaults = setting.getDefaultKnowledgeBaseCodes();
        return defaults == null ? List.of() : defaults.stream().filter(StringUtils::hasText).toList();
    }

    private static RetrievalOptions options(AiSetting.Spec setting) {
        var retrieval = setting.getRetrieval() == null ? new AiSetting.Retrieval() : setting.getRetrieval();
        var prompt = setting.getPrompt() == null ? new AiSetting.Prompt() : setting.getPrompt();
        return new RetrievalOptions(
                Math.max(1, retrieval.getRrfK()),
                retrieval.getScoreThreshold(),
                Math.max(1, retrieval.getL1Limit()),
                Math.max(1, prompt.getKnowledgeBaseCharBudget()));
    }

    private static int candidateLimit(AiSetting.Spec setting) {
        var retrieval = setting.getRetrieval() == null ? new AiSetting.Retrieval() : setting.getRetrieval();
        return Math.max(1, retrieval.getL1Limit()) * 3;
    }

    String systemContent(String agentPrompt, MemoryInjection memory) {
        var sections = new ArrayList<String>();
        if (StringUtils.hasText(agentPrompt)) {
            sections.add(agentPrompt);
        } else {
            sections.add(ChatPrompts.defaultSystemPrompt());
        }
        var memorySystem = memoryContextBuilder.systemMessage(memory);
        if (StringUtils.hasText(memorySystem)) {
            sections.add(memorySystem);
        }
        return sections.stream().filter(StringUtils::hasText).collect(Collectors.joining("\n\n"));
    }

    private record ConversationState(Conversation conversation, AiAgent agent, List<Message> history) {}

    private record Prepared(
            String userId,
            AiAgent agent,
            Conversation conversation,
            int turnIndex,
            @Nullable KnowledgeBaseContentRetriever retriever,
            @Nullable KnowledgeBaseRetrievalTool knowledgeTool,
            String requestId,
            String idempotencyKey,
            AiSetting.Memory memorySetting,
            List<AiChatMessage> messages,
            List<AiTool> tools,
            AiRequestParams params,
            String modeCode,
            @Nullable String routeGroup,
            @Nullable String modelOverride,
            boolean regenerate,
            AiAgentContextService.HarnessSettings harness) {

        private Prepared {
            messages = messages == null ? List.of() : List.copyOf(messages);
            tools = tools == null ? List.of() : List.copyOf(tools);
            harness = harness == null ? AiAgentContextService.HarnessSettings.DEFAULT : harness;
        }

        private String agentName() {
            return agent.getMetadata().getName();
        }

        private List<Citation> citations() {
            if (retriever != null) {
                return retriever.citations();
            }
            return knowledgeTool == null ? List.of() : knowledgeTool.citations();
        }
    }
}
