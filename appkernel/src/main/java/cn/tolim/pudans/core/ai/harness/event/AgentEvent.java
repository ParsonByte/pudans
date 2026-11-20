package cn.tolim.pudans.core.ai.harness.event;

import org.jspecify.annotations.Nullable;

public sealed interface AgentEvent {

    AgentEventType type();

    String runId();

    record AgentStart(String runId, String agentName) implements AgentEvent {

        @Override
        public AgentEventType type() {
            return AgentEventType.AGENT_START;
        }
    }

    record AgentEnd(String runId, String agentName) implements AgentEvent {

        @Override
        public AgentEventType type() {
            return AgentEventType.AGENT_END;
        }
    }

    record AgentResult(String runId, String content) implements AgentEvent {

        @Override
        public AgentEventType type() {
            return AgentEventType.AGENT_RESULT;
        }
    }

    record ModelCallStart(String runId, int iteration) implements AgentEvent {

        @Override
        public AgentEventType type() {
            return AgentEventType.MODEL_CALL_START;
        }
    }

    record ModelCallEnd(String runId, int iteration, @Nullable String finishReason) implements AgentEvent {

        @Override
        public AgentEventType type() {
            return AgentEventType.MODEL_CALL_END;
        }
    }

    record TextBlockStart(String runId) implements AgentEvent {

        @Override
        public AgentEventType type() {
            return AgentEventType.TEXT_BLOCK_START;
        }
    }

    record TextBlockDelta(String runId, String delta) implements AgentEvent {

        @Override
        public AgentEventType type() {
            return AgentEventType.TEXT_BLOCK_DELTA;
        }
    }

    record TextBlockEnd(String runId, String text) implements AgentEvent {

        @Override
        public AgentEventType type() {
            return AgentEventType.TEXT_BLOCK_END;
        }
    }

    record ThinkingBlockStart(String runId) implements AgentEvent {

        @Override
        public AgentEventType type() {
            return AgentEventType.THINKING_BLOCK_START;
        }
    }

    record ThinkingBlockDelta(String runId, String delta) implements AgentEvent {

        @Override
        public AgentEventType type() {
            return AgentEventType.THINKING_BLOCK_DELTA;
        }
    }

    record ThinkingBlockEnd(String runId, String text) implements AgentEvent {

        @Override
        public AgentEventType type() {
            return AgentEventType.THINKING_BLOCK_END;
        }
    }

    record DataBlockStart(String runId, String mediaType) implements AgentEvent {

        @Override
        public AgentEventType type() {
            return AgentEventType.DATA_BLOCK_START;
        }
    }

    record DataBlockDelta(String runId, String mediaType, String data) implements AgentEvent {

        @Override
        public AgentEventType type() {
            return AgentEventType.DATA_BLOCK_DELTA;
        }
    }

    record DataBlockEnd(String runId, String mediaType) implements AgentEvent {

        @Override
        public AgentEventType type() {
            return AgentEventType.DATA_BLOCK_END;
        }
    }

    record ToolCallStart(String runId, String id, String name, String arguments) implements AgentEvent {

        @Override
        public AgentEventType type() {
            return AgentEventType.TOOL_CALL_START;
        }
    }

    record ToolCallDelta(String runId, String id, String partialArguments) implements AgentEvent {

        @Override
        public AgentEventType type() {
            return AgentEventType.TOOL_CALL_DELTA;
        }
    }

    record ToolCallEnd(String runId, String id, String name, String arguments) implements AgentEvent {

        @Override
        public AgentEventType type() {
            return AgentEventType.TOOL_CALL_END;
        }
    }

    record ToolResultStart(String runId, String id, String name) implements AgentEvent {

        @Override
        public AgentEventType type() {
            return AgentEventType.TOOL_RESULT_START;
        }
    }

    record ToolResultProgress(String runId, String id, String message) implements AgentEvent {

        @Override
        public AgentEventType type() {
            return AgentEventType.TOOL_RESULT_PROGRESS;
        }
    }

    record ToolResultTextDelta(String runId, String id, String delta) implements AgentEvent {

        @Override
        public AgentEventType type() {
            return AgentEventType.TOOL_RESULT_TEXT_DELTA;
        }
    }

    record ToolResultEnd(String runId, String id, String name, String output, boolean error)
            implements AgentEvent {

        @Override
        public AgentEventType type() {
            return AgentEventType.TOOL_RESULT_END;
        }
    }

    record RequireUserConfirm(String runId, String id, String name, String message)
            implements AgentEvent {

        @Override
        public AgentEventType type() {
            return AgentEventType.REQUIRE_USER_CONFIRM;
        }
    }

    record UserConfirmResult(String runId, String id, boolean approved) implements AgentEvent {

        @Override
        public AgentEventType type() {
            return AgentEventType.USER_CONFIRM_RESULT;
        }
    }

    record RequireExternalExecution(String runId, String id, String name, String arguments)
            implements AgentEvent {

        @Override
        public AgentEventType type() {
            return AgentEventType.REQUIRE_EXTERNAL_EXECUTION;
        }
    }

    record ExternalExecutionResult(String runId, String id, String output, boolean error)
            implements AgentEvent {

        @Override
        public AgentEventType type() {
            return AgentEventType.EXTERNAL_EXECUTION_RESULT;
        }
    }

    record AllToolsDenied(String runId) implements AgentEvent {

        @Override
        public AgentEventType type() {
            return AgentEventType.ALL_TOOLS_DENIED;
        }
    }

    record ExceedMaxIters(String runId, int maxIters) implements AgentEvent {

        @Override
        public AgentEventType type() {
            return AgentEventType.EXCEED_MAX_ITERS;
        }
    }

    record RequestStop(String runId) implements AgentEvent {

        @Override
        public AgentEventType type() {
            return AgentEventType.REQUEST_STOP;
        }
    }

    record SubagentExposed(String runId, String subagentName) implements AgentEvent {

        @Override
        public AgentEventType type() {
            return AgentEventType.SUBAGENT_EXPOSED;
        }
    }

    record HintBlock(String runId, String hint) implements AgentEvent {

        @Override
        public AgentEventType type() {
            return AgentEventType.HINT_BLOCK;
        }
    }

    record Custom(String runId, String name, String payload) implements AgentEvent {

        @Override
        public AgentEventType type() {
            return AgentEventType.CUSTOM;
        }
    }

    record ToolCompensated(String runId, String id, String name, String message) implements AgentEvent {

        @Override
        public AgentEventType type() {
            return AgentEventType.TOOL_COMPENSATED;
        }
    }
}