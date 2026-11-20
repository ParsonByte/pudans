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
import cn.tolim.pudans.core.ai.rag.KnowledgeBaseRetrievalAugmentor;
import cn.tolim.pudans.core.ai.rag.KnowledgeBaseRetrievalService;
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
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServiceStreamingEvent;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.FlowAdapters;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
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

    public Mono<AiChatCompletionResult> complete(AiChatCommand command) {
        return prepare(command, false)
                .flatMap(prepared -> completeWithAiServices(prepared)
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

    private Mono<AgentRound> completeWithAiServices(Prepared prepared) {
        var currentUser = lastUserMessage(prepared.messages());
        if (currentUser == null) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, CODE_MESSAGES_INVALID, "缺少本轮用户消息。"));
        }
        var system = systemText(prepared.messages());
        var memory = chatMemory(prepared.messages(), currentUser);
        var outcomes = new CopyOnWriteArrayList<ToolOutcome>();
        var state = new GatewayRunState();
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
        var tools = toolExecutors(prepared, outcomes);
        return Mono.fromCallable(() -> {
                    var builder = AiServices.builder(ChatAssistant.class)
                            .chatModel(new GatewayChatModel(gatewayService, context, state))
                            .chatMemory(memory)
                            .chatRequestTransformer(request -> applyParams(request, prepared.params()))
                            .hallucinatedToolNameStrategy(request -> {
                                var outcome = executeTool(prepared, request, outcomes);
                                return ToolExecutionResultMessage.builder()
                                        .id(request.id())
                                        .toolName(request.name())
                                        .text(outcome.output())
                                        .isError(outcome.isError())
                                        .build();
                            });
                    if (StringUtils.hasText(system)) {
                        builder.systemMessage(system);
                    }
                    if (prepared.retriever() != null) {
                        builder.retrievalAugmentor(
                                new KnowledgeBaseRetrievalAugmentor(prepared.retriever()));
                    }
                    if (!tools.isEmpty()) {
                        builder.tools(tools);
                    }
                    return builder.build()
                            .complete(GatewayChatMessageMapper.toContents(currentUser));
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(result -> agentRound(result, state, outcomes));
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

    private static MessageWindowChatMemory chatMemory(
            List<AiChatMessage> messages, AiChatMessage currentUser) {
        var memory = MessageWindowChatMemory.withMaxMessages(Math.max(2, messages.size() + 2));
        for (var message : messages) {
            if (message == currentUser || "system".equals(message.role())) {
                continue;
            }
            memory.add(GatewayChatMessageMapper.toChatMessage(message));
        }
        return memory;
    }

    private static void appendContent(StringBuilder content, @Nullable String text) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        if (!content.isEmpty()) {
            content.append("\n\n");
        }
        content.append(text);
    }

    private Map<ToolSpecification, ToolExecutor> toolExecutors(
            Prepared prepared, CopyOnWriteArrayList<ToolOutcome> outcomes) {
        var specifications = LangChain4jToolMapper.toSpecifications(prepared.tools());
        var tools = new LinkedHashMap<ToolSpecification, ToolExecutor>();
        for (var specification : specifications) {
            tools.put(specification, new ToolExecutor() {

                @Override
                public String execute(ToolExecutionRequest request, Object memoryId) {
                    return executeTool(prepared, request, outcomes).output();
                }

                @Override
                public ToolExecutionResult executeWithContext(
                        ToolExecutionRequest request, InvocationContext context) {
                    var outcome = executeTool(prepared, request, outcomes);
                    return ToolExecutionResult.builder()
                            .resultText(outcome.output())
                            .isError(outcome.isError())
                            .build();
                }
            });
        }
        return tools;
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

    private static dev.langchain4j.model.chat.request.ChatRequest applyParams(
            dev.langchain4j.model.chat.request.ChatRequest request, AiRequestParams params) {
        var builder = request.toBuilder();
        if (params.temperature() != null) {
            builder.temperature(params.temperature());
        }
        if (params.topP() != null) {
            builder.topP(params.topP());
        }
        if (params.topK() != null) {
            builder.topK(params.topK());
        }
        if (params.maxTokens() != null) {
            builder.maxOutputTokens(params.maxTokens());
        }
        if (!params.stop().isEmpty()) {
            builder.stopSequences(params.stop());
        }
        return builder.build();
    }

    private static AgentRound agentRound(
            Result<String> result, GatewayRunState state, List<ToolOutcome> outcomes) {
        var meta = state.meta();
        var usage = result.tokenUsage() == null
                ? state.usage()
                : new AiTokenUsage(
                        result.tokenUsage().inputTokenCount(),
                        result.tokenUsage().outputTokenCount(),
                        null,
                        null);
        var reasoning = result.finalResponse() == null
                ? ""
                : result.finalResponse().aiMessage().thinking();
        var content = new StringBuilder();
        for (var intermediate : result.intermediateResponses()) {
            appendContent(content, intermediate.aiMessage().text());
        }
        appendContent(content, result.content());
        return new AgentRound(
                content.toString(),
                meta == null ? null : meta.model(),
                meta == null ? null : meta.provider(),
                meta == null ? null : meta.account(),
                usage,
                state.points(),
                roundMetadata(reasoning, outcomes));
    }

    public Flux<AiChatStreamEvent> stream(AiChatCommand command) {
        return Flux.defer(() -> prepare(command, true)
                .flatMapMany(this::streamWithAiServices));
    }

    private Flux<AiChatStreamEvent> streamWithAiServices(Prepared prepared) {
        var currentUser = lastUserMessage(prepared.messages());
        if (currentUser == null) {
            return Flux.error(new ApiException(
                    HttpStatus.BAD_REQUEST, CODE_MESSAGES_INVALID, "缺少本轮用户消息。"));
        }
        var system = systemText(prepared.messages());
        var memory = chatMemory(prepared.messages(), currentUser);
        var outcomes = new CopyOnWriteArrayList<ToolOutcome>();
        var state = new GatewayRunState();
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
        var tools = toolExecutors(prepared, outcomes);
        var metaEmitted = new AtomicBoolean();
        return Flux.defer(() -> {
                    var builder = AiServices.builder(StreamingChatAssistant.class)
                            .streamingChatModel(new GatewayStreamingChatModel(
                                    gatewayService, context, state))
                            .chatMemory(memory)
                            .chatRequestTransformer(request -> applyParams(request, prepared.params()))
                            .hallucinatedToolNameStrategy(request -> {
                                var outcome = executeTool(prepared, request, outcomes);
                                return ToolExecutionResultMessage.builder()
                                        .id(request.id())
                                        .toolName(request.name())
                                        .text(outcome.output())
                                        .isError(outcome.isError())
                                        .build();
                            });
                    if (StringUtils.hasText(system)) {
                        builder.systemMessage(system);
                    }
                    if (prepared.retriever() != null) {
                        builder.retrievalAugmentor(
                                new KnowledgeBaseRetrievalAugmentor(prepared.retriever()));
                    }
                    if (!tools.isEmpty()) {
                        builder.tools(tools);
                    }
                    var publisher = builder.build()
                            .stream(GatewayChatMessageMapper.toContents(currentUser));
                    return Flux.from(FlowAdapters.toPublisher(publisher))
                            .concatMap(event -> mapStreamEvent(
                                    event, prepared, state, outcomes, metaEmitted));
                })
                .onErrorResume(error -> Flux.just(new AiChatStreamEvent.Error(
                        "ai-chat-stream-failed",
                        errorMessage(error),
                        false,
                        !prepared.content().isEmpty())));
    }

    private Flux<AiChatStreamEvent> mapStreamEvent(
            AiServiceStreamingEvent event,
            Prepared prepared,
            GatewayRunState state,
            List<ToolOutcome> outcomes,
            AtomicBoolean metaEmitted) {
        var meta = metaEvents(prepared, state, metaEmitted);
        if (event instanceof AiServiceStreamingEvent.PartialResponseEvent partial) {
            var text = partial.partialResponse().text();
            prepared.content().append(text);
            return Flux.concat(meta, Flux.just(new AiChatStreamEvent.Delta(text, null)));
        }
        if (event instanceof AiServiceStreamingEvent.PartialThinkingEvent partial) {
            return Flux.concat(meta, Flux.just(new AiChatStreamEvent.Delta(
                    null, partial.partialThinking().text())));
        }
        if (event instanceof AiServiceStreamingEvent.BeforeToolExecutionEvent before) {
            var request = before.beforeToolExecution().request();
            return Flux.concat(
                    meta,
                    Flux.just(new AiChatStreamEvent.Stage("tool", "正在执行工具调用。")),
                    Flux.just(new AiChatStreamEvent.ToolCall(
                            request.id(),
                            request.name(),
                            serverOf(request.name()),
                            LangChain4jToolMapper.arguments(request.arguments()))));
        }
        if (event instanceof AiServiceStreamingEvent.AfterToolExecutionEvent after) {
            var execution = after.toolExecution();
            var request = execution.request();
            var outcome = outcomes.stream()
                    .filter(item -> item.id().equals(request.id()))
                    .reduce((first, second) -> second)
                    .orElse(null);
            return Flux.concat(meta, Flux.just(new AiChatStreamEvent.ToolResult(
                    request.id(),
                    request.name(),
                    outcome == null ? execution.result() : outcome.output(),
                    outcome == null ? execution.hasFailed() : outcome.isError(),
                    outcome == null ? 0 : outcome.durationMs())));
        }
        if (event instanceof AiServiceStreamingEvent.FinalResponseEvent finalEvent) {
            return Flux.concat(meta, finalStreamEvents(prepared, state, outcomes, finalEvent));
        }
        return meta;
    }

    private Flux<AiChatStreamEvent> metaEvents(
            Prepared prepared, GatewayRunState state, AtomicBoolean metaEmitted) {
        if (!metaEmitted.compareAndSet(false, true)) {
            return Flux.empty();
        }
        var meta = state.meta() == null ? fallbackMeta(prepared) : state.meta();
        var events = new ArrayList<AiChatStreamEvent>();
        events.add(new AiChatStreamEvent.Meta(streamMeta(meta)));
        prepared.citations().forEach(citation -> events.add(new AiChatStreamEvent.CitationEvent(citation)));
        return Flux.fromIterable(events);
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

    private Flux<AiChatStreamEvent> finalStreamEvents(
            Prepared prepared,
            GatewayRunState state,
            List<ToolOutcome> outcomes,
            AiServiceStreamingEvent.FinalResponseEvent event) {
        var response = event.chatResponse();
        var content = prepared.content().isEmpty()
                ? (response.aiMessage().text() == null ? "" : response.aiMessage().text())
                : prepared.content().toString();
        var usage = response.tokenUsage() == null
                ? state.usage()
                : new AiTokenUsage(
                        response.tokenUsage().inputTokenCount(),
                        response.tokenUsage().outputTokenCount(),
                        null,
                        null);
        var metadata = roundMetadata(response.aiMessage().thinking(), outcomes);
        var hasUsage = usage != null
                && (usage.inputTokens() != null
                        || usage.outputTokens() != null
                        || usage.cacheReadTokens() != null
                        || usage.cacheWriteTokens() != null);
        var hasPoints = state.points().signum() > 0;
        Flux<AiChatStreamEvent> usageEvent = hasUsage || hasPoints
                ? Flux.<AiChatStreamEvent>just(new AiChatStreamEvent.Usage(
                        usage, state.points(), state.billingSource()))
                : Flux.empty();
        return usageEvent.concatWith(appendAssistant(
                        prepared.conversation(),
                        prepared.turnIndex(),
                        content,
                        prepared.requestId(),
                        response.modelName(),
                        usage == null ? null : usage.outputTokens(),
                        metadata)
                .flatMapMany(message -> conversationService
                        .markPending(prepared.conversation(), 2)
                        .then(enqueueExtraction(prepared, message))
                        .then(dispatchSessionEnd(prepared))
                        .thenMany(Flux.just((AiChatStreamEvent) new AiChatStreamEvent.Done(
                                String.valueOf(message.id()),
                                finishReason(response),
                                false)))));
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

    private Mono<Prepared> prepare(AiChatCommand command, boolean stream) {
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
                                                                        attachmentParts,
                                                                        stream)))))))));
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
        return conversationService.history(conversation, limit).collectList();
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
            List<AiContentPart> attachmentParts,
            boolean stream) {
        var prompt = setting.getPrompt() == null ? new AiSetting.Prompt() : setting.getPrompt();
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
        var retriever = rag.isEmpty()
                ? null
                : new KnowledgeBaseContentRetriever(retrievalService, embeddingService, rag);
        var prepared = new Prepared(
                command.userId(),
                state.agent(),
                state.conversation(),
                turnIndex,
                retriever,
                requestId,
                idempotencyKey,
                new StringBuilder(),
                memorySetting,
                List.copyOf(messages),
                context.tools(),
                params,
                mode.getMetadata().getName(),
                command.request().routeGroup(),
                command.request().model(),
                stream);
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

    private record AgentRound(
            String content,
            @Nullable String model,
            @Nullable String provider,
            @Nullable String account,
            @Nullable AiTokenUsage usage,
            BigDecimal points,
            @Nullable JsonNode metadata) {}

    interface ChatAssistant {

        Result<String> complete(List<Content> contents);
    }

    interface StreamingChatAssistant {

        Flow.Publisher<AiServiceStreamingEvent> stream(List<Content> contents);
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
            Integer outputTokens) {
        return conversationService.appendAssistantMessage(
                conversation, turnIndex, 1, content, requestId, modelCode, outputTokens);
    }

    private Mono<Message> appendAssistant(
            Conversation conversation,
            int turnIndex,
            String content,
            String requestId,
            String modelCode,
            Integer outputTokens,
            @Nullable String reasoning) {
        return appendAssistant(
                conversation,
                turnIndex,
                content,
                requestId,
                modelCode,
                outputTokens,
                roundMetadata(reasoning, List.of()));
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
            String requestId,
            String idempotencyKey,
            StringBuilder content,
            AiSetting.Memory memorySetting,
            List<AiChatMessage> messages,
            List<AiTool> tools,
            AiRequestParams params,
            String modeCode,
            @Nullable String routeGroup,
            @Nullable String modelOverride,
            boolean stream) {

        private Prepared {
            messages = messages == null ? List.of() : List.copyOf(messages);
            tools = tools == null ? List.of() : List.copyOf(tools);
        }

        private String agentName() {
            return agent.getMetadata().getName();
        }

        private List<Citation> citations() {
            return retriever == null ? List.of() : retriever.citations();
        }
    }
}
