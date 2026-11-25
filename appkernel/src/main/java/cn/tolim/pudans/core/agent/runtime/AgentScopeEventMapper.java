package cn.tolim.pudans.core.agent.runtime;

import cn.tolim.pudans.core.ai.usage.AiUsage;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.AgentStartEvent;
import io.agentscope.core.event.ExceedMaxItersEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.TextBlockStartEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AgentScopeEventMapper {

    private AgentScopeEventMapper() {}

    // 将 AgentScope 事件转换为项目 SSE 事件，不向接口层暴露 SDK 类型。
    static MappedEvent map(AgentEvent event) {
        return switch (event.getType()) {
            case AGENT_START -> mapStart((AgentStartEvent) event);
            case TEXT_BLOCK_START -> mapEvent(
                    AgentRunEvents.MESSAGE_START,
                    map(
                            "replyId", ((TextBlockStartEvent) event).getReplyId(),
                            "blockId", ((TextBlockStartEvent) event).getBlockId()));
            case TEXT_BLOCK_DELTA -> mapEvent(
                    AgentRunEvents.MESSAGE_DELTA,
                    map(
                            "replyId", ((TextBlockDeltaEvent) event).getReplyId(),
                            "blockId", ((TextBlockDeltaEvent) event).getBlockId(),
                            "delta", ((TextBlockDeltaEvent) event).getDelta()));
            case THINKING_BLOCK_DELTA -> mapEvent(
                    AgentRunEvents.REASONING_DELTA,
                    map(
                            "replyId", ((ThinkingBlockDeltaEvent) event).getReplyId(),
                            "blockId", ((ThinkingBlockDeltaEvent) event).getBlockId(),
                            "delta", ((ThinkingBlockDeltaEvent) event).getDelta()));
            case TOOL_CALL_START -> mapEvent(
                    AgentRunEvents.TOOL_START,
                    map(
                            "replyId", ((ToolCallStartEvent) event).getReplyId(),
                            "toolCallId", ((ToolCallStartEvent) event).getToolCallId(),
                            "toolName", ((ToolCallStartEvent) event).getToolCallName()));
            case TOOL_RESULT_END -> mapEvent(
                    AgentRunEvents.TOOL_RESULT,
                    map(
                            "replyId", ((ToolResultEndEvent) event).getReplyId(),
                            "toolCallId", ((ToolResultEndEvent) event).getToolCallId(),
                            "toolName", ((ToolResultEndEvent) event).getToolCallName(),
                            "state", state(((ToolResultEndEvent) event).getState())));
            case REQUIRE_USER_CONFIRM -> mapConfirm((RequireUserConfirmEvent) event);
            case AGENT_RESULT -> mapResult((AgentResultEvent) event);
            case EXCEED_MAX_ITERS -> mapEvent(
                    AgentRunEvents.RUN_FAILED,
                    map(
                            "errorCode", "agent-max-iterations",
                            "maxIters", ((ExceedMaxItersEvent) event).getMaxIters(),
                            "currentIter", ((ExceedMaxItersEvent) event).getCurrentIter()));
            default -> null;
        };
    }

    static String statusForType(String type) {
        if (AgentRunEvents.RUN_COMPLETED.equals(type)) {
            return "completed";
        }
        if (AgentRunEvents.RUN_FAILED.equals(type)) {
            return "failed";
        }
        if (AgentRunEvents.RUN_CANCELLED.equals(type)) {
            return "cancelled";
        }
        return "running";
    }

    static Map<String, Object> assistantContent(Msg message) {
        var text = message == null ? "" : message.getTextContent();
        var content = new LinkedHashMap<String, Object>();
        content.put("type", "text");
        content.put("text", text == null ? "" : text);
        return content;
    }

    static List<Map<String, Object>> toolCalls(Msg message) {
        if (message == null) {
            return List.of();
        }
        var results = new LinkedHashMap<String, ToolResultBlock>();
        for (var result : message.getContentBlocks(ToolResultBlock.class)) {
            if (result.getId() != null) {
                results.put(result.getId(), result);
            }
        }
        var calls = new ArrayList<Map<String, Object>>();
        for (var use : message.getContentBlocks(ToolUseBlock.class)) {
            var call = toolCall(use);
            var result = results.remove(use.getId());
            if (result != null) {
                call.put("result", toolResult(result));
            }
            calls.add(call);
        }
        results.values().forEach(result -> calls.add(toolResult(result)));
        return List.copyOf(calls);
    }

    static Map<String, Object> usage(Msg message) {
        var chatUsage = message == null ? null : message.getChatUsage();
        if (chatUsage == null) {
            return Map.of();
        }
        return AiUsage.of(
                        chatUsage.getInputTokens(),
                        chatUsage.getOutputTokens(),
                        chatUsage.getCachedTokens(),
                        chatUsage.getTotalTokens())
                .toMap();
    }

    static Map<String, Object> toolCall(ToolUseBlock toolCall) {
        return map(
                "id", toolCall.getId(),
                "name", toolCall.getName(),
                "input", toolCall.getInput(),
                "state", state(toolCall.getState()));
    }

    private static MappedEvent mapStart(AgentStartEvent event) {
        return mapEvent(
                AgentRunEvents.RUN_STARTED,
                map(
                        "sessionId", event.getSessionId(),
                        "replyId", event.getReplyId(),
                        "agentName", event.getName(),
                        "role", event.getRole()));
    }

    private static MappedEvent mapConfirm(RequireUserConfirmEvent event) {
        var calls = event.getToolCalls() == null ? List.<ToolUseBlock>of() : event.getToolCalls();
        var data = map(
                "replyId", event.getReplyId(),
                "toolCalls", calls.stream().map(AgentScopeEventMapper::toolCall).toList());
        return new MappedEvent(AgentRunEvents.TOOL_APPROVAL_REQUIRED, data, null, calls);
    }

    private static MappedEvent mapResult(AgentResultEvent event) {
        var result = event.getResult();
        var type = terminalType(result);
        var data = map(
                "generateReason", reason(result),
                "text", result == null ? null : result.getTextContent(),
                "toolCalls", toolCalls(result),
                "usage", usage(result));
        return new MappedEvent(type, data, result, List.of());
    }

    private static String terminalType(Msg result) {
        var reason = result == null ? null : result.getGenerateReason();
        if (reason == GenerateReason.INTERRUPTED) {
            return AgentRunEvents.RUN_CANCELLED;
        }
        if (reason == GenerateReason.MAX_ITERATIONS
                || reason == GenerateReason.ALL_TOOLS_DENIED) {
            return AgentRunEvents.RUN_FAILED;
        }
        if (reason == GenerateReason.PERMISSION_ASKING
                || reason == GenerateReason.TOOL_SUSPENDED) {
            return null;
        }
        return AgentRunEvents.RUN_COMPLETED;
    }

    private static Map<String, Object> toolResult(ToolResultBlock result) {
        return map(
                "id", result.getId(),
                "name", result.getName(),
                "state", state(result.getState()),
                "output", output(result));
    }

    private static String output(ToolResultBlock result) {
        if (result.getOutput() == null) {
            return "";
        }
        var text = new StringBuilder();
        for (ContentBlock block : result.getOutput()) {
            if (block instanceof TextBlock value && value.getText() != null) {
                text.append(value.getText());
            }
        }
        return text.toString();
    }

    private static String state(Object state) {
        if (state == null) {
            return null;
        }
        if (state instanceof io.agentscope.core.message.ToolCallState value) {
            return value.getValue();
        }
        if (state instanceof io.agentscope.core.message.ToolResultState value) {
            return value.getValue();
        }
        return state.toString();
    }

    private static String reason(Msg result) {
        return result == null || result.getGenerateReason() == null
                ? null
                : result.getGenerateReason().name().toLowerCase(java.util.Locale.ROOT);
    }

    private static MappedEvent mapEvent(String type, Map<String, Object> data) {
        return new MappedEvent(type, data, null, List.of());
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

    record MappedEvent(
            String type,
            Map<String, Object> data,
            Msg result,
            List<ToolUseBlock> pendingConfirmations) {}
}
