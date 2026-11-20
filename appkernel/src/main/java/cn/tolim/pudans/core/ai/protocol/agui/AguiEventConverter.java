package cn.tolim.pudans.core.ai.protocol.agui;

import cn.tolim.pudans.core.ai.harness.event.AgentEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

// 把 Harness 事件转换为 AG-UI 1.0 事件，延迟到首个增量才发送 TEXT_MESSAGE_START。
public final class AguiEventConverter {

    private static final String ASSISTANT_ROLE = "assistant";

    private static final String TOOL_ROLE = "tool";

    private static final String RUN_FAILED_MESSAGE = "智能体运行失败。";

    private final String threadId;

    private final String runId;

    private final TextMessageState text = new TextMessageState();

    private final TextMessageState thinking = new TextMessageState();

    private final Set<String> streamedToolCallIds = new HashSet<>();

    private boolean started;

    private boolean terminal;

    public AguiEventConverter(String threadId, String runId) {
        this.threadId = threadId;
        this.runId = runId;
    }

    public synchronized List<AguiEvent> convert(AgentEvent event) {
        if (terminal) {
            return List.of();
        }
        return switch (event) {
            case AgentEvent.AgentStart ignored -> start();
            case AgentEvent.TextBlockDelta delta -> append(text, delta.delta());
            case AgentEvent.TextBlockEnd end -> close(text, end.text());
            case AgentEvent.ThinkingBlockDelta delta -> append(thinking, delta.delta());
            case AgentEvent.ThinkingBlockEnd end -> close(thinking, end.text());
            case AgentEvent.ToolCallStart start -> toolCallStart(start);
            case AgentEvent.ToolCallDelta delta -> toolCallArgs(delta.id(), delta.partialArguments());
            case AgentEvent.ToolCallEnd end -> toolCallEnd(end);
            case AgentEvent.ToolResultEnd end -> List.of(toolCallResult(end));
            case AgentEvent.AgentResult result -> finish(result.content());
            case AgentEvent.AgentEnd ignored -> finish(null);
            default -> List.of();
        };
    }

    public synchronized List<AguiEvent> finish(@Nullable String result) {
        if (terminal) {
            return List.of();
        }
        terminal = true;
        var events = new ArrayList<AguiEvent>(3);
        events.addAll(close(text, null));
        events.addAll(close(thinking, null));
        events.add(new AguiEvent.RunFinished(threadId, runId, result));
        return events;
    }

    public synchronized List<AguiEvent> fail(@Nullable String message, @Nullable String code) {
        if (terminal) {
            return List.of();
        }
        terminal = true;
        var events = new ArrayList<AguiEvent>(3);
        events.addAll(close(text, null));
        events.addAll(close(thinking, null));
        events.add(new AguiEvent.RunError(
                threadId,
                runId,
                message == null || message.isBlank() ? RUN_FAILED_MESSAGE : message,
                code));
        return events;
    }

    private List<AguiEvent> start() {
        if (started) {
            return List.of();
        }
        started = true;
        return List.of(new AguiEvent.RunStarted(threadId, runId));
    }

    private List<AguiEvent> append(TextMessageState state, @Nullable String delta) {
        if (delta == null || delta.isEmpty()) {
            return List.of();
        }
        var events = new ArrayList<AguiEvent>(2);
        if (state.messageId == null) {
            state.messageId = messageId();
            events.add(textStart(state.messageId));
        }
        state.contentEmitted = true;
        events.add(new AguiEvent.TextMessageContent(threadId, runId, state.messageId, delta));
        return events;
    }

    private List<AguiEvent> close(TextMessageState state, @Nullable String finalText) {
        if (state.messageId == null && (finalText == null || finalText.isBlank())) {
            return List.of();
        }
        var events = new ArrayList<AguiEvent>(2);
        if (state.messageId == null) {
            state.messageId = messageId();
            events.add(textStart(state.messageId));
            events.add(new AguiEvent.TextMessageContent(threadId, runId, state.messageId, finalText));
        } else if (!state.contentEmitted && finalText != null && !finalText.isEmpty()) {
            events.add(new AguiEvent.TextMessageContent(threadId, runId, state.messageId, finalText));
        }
        events.add(new AguiEvent.TextMessageEnd(threadId, runId, state.messageId));
        state.messageId = null;
        state.contentEmitted = false;
        return events;
    }

    private AguiEvent.TextMessageStart textStart(String messageId) {
        return new AguiEvent.TextMessageStart(threadId, runId, messageId, ASSISTANT_ROLE);
    }

    private List<AguiEvent> toolCallStart(AgentEvent.ToolCallStart start) {
        streamedToolCallIds.remove(start.id());
        return List.of(new AguiEvent.ToolCallStart(threadId, runId, start.id(), start.name()));
    }

    private List<AguiEvent> toolCallArgs(String id, @Nullable String delta) {
        if (delta == null || delta.isEmpty()) {
            return List.of();
        }
        streamedToolCallIds.add(id);
        return List.of(new AguiEvent.ToolCallArgs(threadId, runId, id, delta));
    }

    private List<AguiEvent> toolCallEnd(AgentEvent.ToolCallEnd end) {
        var events = new ArrayList<AguiEvent>(2);
        if (!streamedToolCallIds.remove(end.id()) && end.arguments() != null && !end.arguments().isEmpty()) {
            events.add(new AguiEvent.ToolCallArgs(threadId, runId, end.id(), end.arguments()));
        }
        events.add(new AguiEvent.ToolCallEnd(threadId, runId, end.id()));
        return events;
    }

    private AguiEvent.ToolCallResult toolCallResult(AgentEvent.ToolResultEnd end) {
        return new AguiEvent.ToolCallResult(
                threadId,
                runId,
                end.id(),
                end.output() == null ? "" : end.output(),
                TOOL_ROLE,
                messageId());
    }

    private static String messageId() {
        return "msg-" + UUID.randomUUID();
    }

    private static final class TextMessageState {

        private @Nullable String messageId;

        private boolean contentEmitted;
    }
}