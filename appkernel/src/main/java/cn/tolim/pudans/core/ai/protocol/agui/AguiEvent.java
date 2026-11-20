package cn.tolim.pudans.core.ai.protocol.agui;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface AguiEvent {

    @JsonProperty("type")
    AguiEventType type();

    @JsonProperty("threadId")
    String threadId();

    @JsonProperty("runId")
    String runId();

    record RunStarted(String threadId, String runId) implements AguiEvent {

        @Override
        public AguiEventType type() {
            return AguiEventType.RUN_STARTED;
        }
    }

    record RunFinished(String threadId, String runId, @Nullable String result)
            implements AguiEvent {

        @Override
        public AguiEventType type() {
            return AguiEventType.RUN_FINISHED;
        }
    }

    record RunError(String threadId, String runId, String message, @Nullable String code)
            implements AguiEvent {

        @Override
        public AguiEventType type() {
            return AguiEventType.RUN_ERROR;
        }
    }

    record TextMessageStart(String threadId, String runId, String messageId, String role)
            implements AguiEvent {

        @Override
        public AguiEventType type() {
            return AguiEventType.TEXT_MESSAGE_START;
        }
    }

    record TextMessageContent(String threadId, String runId, String messageId, String delta)
            implements AguiEvent {

        @Override
        public AguiEventType type() {
            return AguiEventType.TEXT_MESSAGE_CONTENT;
        }
    }

    record TextMessageEnd(String threadId, String runId, String messageId) implements AguiEvent {

        @Override
        public AguiEventType type() {
            return AguiEventType.TEXT_MESSAGE_END;
        }
    }

    record ToolCallStart(String threadId, String runId, String toolCallId, String toolCallName)
            implements AguiEvent {

        @Override
        public AguiEventType type() {
            return AguiEventType.TOOL_CALL_START;
        }
    }

    record ToolCallArgs(String threadId, String runId, String toolCallId, String delta)
            implements AguiEvent {

        @Override
        public AguiEventType type() {
            return AguiEventType.TOOL_CALL_ARGS;
        }
    }

    record ToolCallEnd(String threadId, String runId, String toolCallId) implements AguiEvent {

        @Override
        public AguiEventType type() {
            return AguiEventType.TOOL_CALL_END;
        }
    }

    record ToolCallResult(
            String threadId, String runId, String toolCallId, String content, String role, String messageId)
            implements AguiEvent {

        @Override
        public AguiEventType type() {
            return AguiEventType.TOOL_CALL_RESULT;
        }
    }
}