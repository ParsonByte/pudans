package cn.tolim.pudans.core.ai.harness.journal;

public enum JournalEventType {
    RUN_START,
    RUN_END,
    RUN_OUTPUT,
    MODEL_CALL_START,
    MODEL_CALL_END,
    TOOL_CALL_START,
    TOOL_RESULT_END,
    TOOL_DECISION,
    USAGE,
    STATE_CHECKPOINT,
    RECOVERY_APPLIED;

    public static JournalEventType parse(String value) {
        for (var type : values()) {
            if (type.name().equals(value)) {
                return type;
            }
        }
        throw new JournalException("未知的线程日志事件类型：" + value + "。");
    }
}