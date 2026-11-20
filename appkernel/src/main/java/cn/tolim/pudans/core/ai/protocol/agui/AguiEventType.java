package cn.tolim.pudans.core.ai.protocol.agui;

public enum AguiEventType {
    RUN_STARTED,
    RUN_FINISHED,
    RUN_ERROR,
    STEP_STARTED,
    STEP_FINISHED,
    TEXT_MESSAGE_START,
    TEXT_MESSAGE_CONTENT,
    TEXT_MESSAGE_END,
    TOOL_CALL_START,
    TOOL_CALL_ARGS,
    TOOL_CALL_END,
    TOOL_CALL_RESULT,
    STATE_SNAPSHOT,
    STATE_DELTA,
    RAW,
    CUSTOM
}