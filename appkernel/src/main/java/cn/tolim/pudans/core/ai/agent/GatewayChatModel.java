package cn.tolim.pudans.core.ai.agent;

import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.infra.api.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.AiChatResponse;
import cn.tolim.pudans.core.ai.gateway.AiGatewayInvocation;
import cn.tolim.pudans.core.ai.gateway.AiGatewayResult;
import cn.tolim.pudans.core.ai.gateway.AiGateway;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.ir.AiRequestParams;
import cn.tolim.pudans.core.ai.gateway.ir.AiTool;
import cn.tolim.pudans.core.ai.provider.langchain4j.LangChain4jToolMapper;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class GatewayChatModel implements ChatModel {

    private final AiGateway aiGateway;

    private final GatewayChatContext context;

    private final GatewayRunState state;

    public GatewayChatModel(AiGateway aiGateway, GatewayChatContext context) {
        this(aiGateway, context, new GatewayRunState());
    }

    public GatewayChatModel(
            AiGateway aiGateway, GatewayChatContext context, GatewayRunState state) {
        this.aiGateway = Objects.requireNonNull(aiGateway, "AI 网关服务不能为空");
        this.context = Objects.requireNonNull(context, "Agent 上下文不能为空");
        this.state = Objects.requireNonNull(state, "Agent 运行状态不能为空");
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        var call = state.nextCall();
        var requestId = call <= 1 ? context.requestId() : context.requestId() + "-r" + (call - 1);
        var idempotencyKey =
                call <= 1 ? context.idempotencyKey() : context.idempotencyKey() + "-r" + (call - 1);
        var invocation = new AiGatewayInvocation(
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
        AiGatewayResult result = aiGateway.complete(invocation).block();
        if (result == null) {
            throw new IllegalStateException("AI 网关返回空响应。");
        }
        state.record(result);
        return toResponse(result.response());
    }

    public AiChatIr toIr(ChatRequest request) {
        var messages = new ArrayList<AiChatMessage>();
        for (var message : request.messages()) {
            messages.add(GatewayChatMessageMapper.toAiChatMessage(message));
        }
        return new AiChatIr(
                messages,
                tools(request.toolSpecifications()),
                params(request.parameters()),
                false);
    }

    public ChatResponse toResponse(AiChatResponse response) {
        var message = response.message();
        var text = new StringBuilder();
        var thinking = new StringBuilder();
        var calls = new ArrayList<ToolExecutionRequest>();
        for (var part : message.parts()) {
            if (part instanceof AiContentPart.Text content) {
                text.append(content.text());
            } else if (part instanceof AiContentPart.Thinking content) {
                thinking.append(content.text());
            } else if (part instanceof AiContentPart.ToolCall call) {
                calls.add(ToolExecutionRequest.builder()
                        .id(call.id())
                        .name(call.name())
                        .arguments(call.arguments() == null ? "{}" : call.arguments().toString())
                        .build());
            }
        }
        var usage = response.usage();
        return ChatResponse.builder()
                .id(response.id())
                .modelName(response.model())
                .aiMessage(AiMessage.builder()
                        .text(text.isEmpty() ? null : text.toString())
                        .thinking(thinking.isEmpty() ? null : thinking.toString())
                        .toolExecutionRequests(calls)
                        .build())
                .tokenUsage(usage == null
                        ? null
                        : new TokenUsage(usage.inputTokens(), usage.outputTokens()))
                .finishReason(finishReason(response.finishReason()))
                .build();
    }

    private static AiRequestParams params(ChatRequestParameters params) {
        return new AiRequestParams(
                params.modelName(),
                params.temperature(),
                params.maxOutputTokens(),
                params.topP(),
                params.topK(),
                params.stopSequences(),
                toolChoice(params.toolChoice()),
                Map.of());
    }

    private static List<AiTool> tools(List<dev.langchain4j.agent.tool.ToolSpecification> tools) {
        var result = new ArrayList<AiTool>();
        for (var tool : tools) {
            result.add(new AiTool(
                    tool.name(),
                    tool.description(),
                    LangChain4jToolMapper.parameters(tool),
                    null));
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
}