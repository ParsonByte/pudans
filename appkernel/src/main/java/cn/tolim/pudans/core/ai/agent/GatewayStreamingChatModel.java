package cn.tolim.pudans.core.ai.agent;

import cn.tolim.pudans.contract.ai.resource.AiProvider;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.AiGatewayEvent;
import cn.tolim.pudans.core.ai.gateway.AiGatewayInvocation;
import cn.tolim.pudans.core.ai.gateway.AiGateway;
import cn.tolim.pudans.core.ai.gateway.AiStreamEvent;
import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiRequestParams;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolException;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import org.reactivestreams.FlowAdapters;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

public class GatewayStreamingChatModel implements StreamingChatModel {

    private final AiGateway gatewayService;

    private final GatewayChatContext context;

    private final GatewayRunState state;

    public GatewayStreamingChatModel(
            AiGateway gatewayService, GatewayChatContext context, GatewayRunState state) {
        this.gatewayService = Objects.requireNonNull(gatewayService, "AI 网关服务不能为空");
        this.context = Objects.requireNonNull(context, "Agent 上下文不能为空");
        this.state = Objects.requireNonNull(state, "Agent 运行状态不能为空");
    }

    @Override
    public Flow.Publisher<ChatModelStreamingEvent> chat(ChatRequest request) {
        return FlowAdapters.toFlowPublisher(events(request));
    }

    @Override
    public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
        Flux.from(FlowAdapters.toPublisher(chat(request))).subscribe(
                event -> {
                    if (event instanceof PartialResponse partial) {
                        handler.onPartialResponse(partial.text());
                    } else if (event instanceof PartialThinking partial) {
                        handler.onPartialThinking(partial);
                    } else if (event instanceof PartialToolCall partial) {
                        handler.onPartialToolCall(partial);
                    } else if (event instanceof CompleteToolCall call) {
                        handler.onCompleteToolCall(call);
                    } else if (event instanceof CompleteResponse response) {
                        handler.onCompleteResponse(response.chatResponse());
                    }
                },
                handler::onError,
                () -> {});
    }

    private Flux<ChatModelStreamingEvent> events(ChatRequest request) {
        return Flux.defer(() -> {
            var invocation = invocation(request);
            var content = new StringBuilder();
            var thinking = new StringBuilder();
            var calls = new LinkedHashMap<Integer, ToolCallState>();
            var usage = new AtomicReference<AiTokenUsage>();
            return Flux.from(gatewayService.stream(invocation)).concatMap(event -> switch (event) {
                case AiGatewayEvent.Meta meta -> {
                    state.recordMeta(meta.meta());
                    yield Flux.empty();
                }
                case AiGatewayEvent.Delta delta -> {
                    var source = delta.event();
                    var events = new ArrayList<ChatModelStreamingEvent>();
                    if (StringUtils.hasText(source.content())) {
                        content.append(source.content());
                        events.add(new PartialResponse(source.content()));
                    }
                    if (StringUtils.hasText(source.reasoning())) {
                        thinking.append(source.reasoning());
                        events.add(new PartialThinking(source.reasoning()));
                    }
                    if (source.toolCall() != null) {
                        var call = calls.computeIfAbsent(
                                source.toolCall().index() == null ? 0 : source.toolCall().index(),
                                ignored -> new ToolCallState());
                        call.accumulate(source.toolCall());
                        if (StringUtils.hasText(call.name)
                                && StringUtils.hasText(source.toolCall().arguments())) {
                            events.add(PartialToolCall.builder()
                                    .index(call.index)
                                    .id(call.id)
                                    .name(call.name)
                                    .partialArguments(source.toolCall().arguments())
                                    .build());
                        }
                    }
                    yield Flux.fromIterable(events);
                }
                case AiGatewayEvent.Usage value -> {
                    usage.set(value.usage());
                    state.recordUsage(value.usage(), value.units(), value.billingSource());
                    yield Flux.empty();
                }
                case AiGatewayEvent.Done done -> {
                    var completeCalls = completeCalls(calls);
                    var finalResponse = new CompleteResponse(
                            response(content, thinking, completeCalls, usage.get(), done.finishReason()));
                    yield Flux.concat(
                            Flux.<ChatModelStreamingEvent>fromIterable(completeCalls),
                            Flux.<ChatModelStreamingEvent>just(finalResponse));
                }
                case AiGatewayEvent.Error error ->
                        Flux.error(new AiProtocolException(error.message()));
            });
        });
    }

    private AiGatewayInvocation invocation(ChatRequest request) {
        var call = state.nextCall();
        var requestId = call <= 1 ? context.requestId() : context.requestId() + "-r" + (call - 1);
        var idempotencyKey =
                call <= 1 ? context.idempotencyKey() : context.idempotencyKey() + "-r" + (call - 1);
        return new AiGatewayInvocation(
                AiProvider.Protocol.OPENAI_CHAT,
                toIr(request),
                context.userId(),
                context.agentSource(),
                context.sessionKey(),
                context.sessionId(),
                requestId,
                idempotencyKey,
                context.modeCode(),
                context.routeGroupCode(),
                context.agentName(),
                context.conversationId(),
                context.modelOverride());
    }

    private static AiChatIr toIr(ChatRequest request) {
        var messages = new ArrayList<AiChatMessage>();
        for (var message : request.messages()) {
            messages.add(GatewayChatMessageMapper.toAiChatMessage(message));
        }
        var parameters = request.parameters();
        var params = new AiRequestParams(
                parameters.modelName(),
                parameters.temperature(),
                parameters.maxOutputTokens(),
                parameters.topP(),
                parameters.topK(),
                parameters.stopSequences(),
                toolChoice(parameters.toolChoice()),
                Map.of());
        var tools = new ArrayList<cn.tolim.pudans.core.ai.gateway.ir.AiTool>();
        for (var tool : request.toolSpecifications()) {
            tools.add(new cn.tolim.pudans.core.ai.gateway.ir.AiTool(
                    tool.name(),
                    tool.description(),
                    cn.tolim.pudans.core.ai.provider.langchain4j.LangChain4jToolMapper.parameters(tool),
                    null));
        }
        return new AiChatIr(messages, tools, params, true);
    }

    private ChatResponse response(
            StringBuilder content,
            StringBuilder thinking,
            List<CompleteToolCall> calls,
            AiTokenUsage usage,
            String reason) {
        var toolRequests = new ArrayList<ToolExecutionRequest>();
        for (var call : calls) {
            toolRequests.add(call.toolExecutionRequest());
        }
        var meta = state.meta();
        return ChatResponse.builder()
                .id(context.requestId())
                .modelName(meta == null ? null : meta.model())
                .aiMessage(AiMessage.builder()
                        .text(content.isEmpty() ? null : content.toString())
                        .thinking(thinking.isEmpty() ? null : thinking.toString())
                        .toolExecutionRequests(toolRequests)
                        .build())
                .tokenUsage(usage == null ? null : new TokenUsage(usage.inputTokens(), usage.outputTokens()))
                .finishReason(finishReason(reason))
                .build();
    }

    private static List<CompleteToolCall> completeCalls(Map<Integer, ToolCallState> calls) {
        var result = new ArrayList<CompleteToolCall>();
        for (var entry : calls.entrySet()) {
            var call = entry.getValue();
            if (!StringUtils.hasText(call.id) || !StringUtils.hasText(call.name)) {
                continue;
            }
            result.add(new CompleteToolCall(
                    entry.getKey(),
                    ToolExecutionRequest.builder()
                            .id(call.id)
                            .name(call.name)
                            .arguments(call.arguments.isEmpty() ? "{}" : call.arguments.toString())
                            .build()));
        }
        return result;
    }

    private static JsonNode toolChoice(ToolChoice choice) {
        if (choice == null) {
            return null;
        }
        return JsonUtils.mapper().getNodeFactory().textNode(switch (choice) {
            case AUTO -> "auto";
            case REQUIRED -> "required";
            case NONE -> "none";
        });
    }

    private static FinishReason finishReason(String reason) {
        if (reason == null) {
            return FinishReason.STOP;
        }
        return switch (reason) {
            case "length", "max_tokens" -> FinishReason.LENGTH;
            case "tool_calls", "tool_use" -> FinishReason.TOOL_EXECUTION;
            case "content_filter" -> FinishReason.CONTENT_FILTER;
            case "stop", "end_turn" -> FinishReason.STOP;
            default -> FinishReason.OTHER;
        };
    }

    private static final class ToolCallState {

        private int index;

        private String id;

        private String name;

        private final StringBuilder arguments = new StringBuilder();

        private void accumulate(AiStreamEvent.ToolCallDelta delta) {
            index = delta.index() == null ? index : delta.index();
            if (StringUtils.hasText(delta.id())) {
                id = delta.id();
            }
            if (StringUtils.hasText(delta.name())) {
                name = delta.name();
            }
            if (delta.arguments() != null) {
                arguments.append(delta.arguments());
            }
        }
    }
}