package cn.tolim.pudans.core.creation.task;

import java.util.UUID;

public final class GenerationTaskKeys {

    public static final String TASK_QUEUE = "pudans:ai:tasks";

    public static final String TASK_DEAD_QUEUE = "pudans:ai:tasks:dead";

    public static final String TASK_GROUP = "pudans-ai-workers";

    public static final String EVENT_CHANNEL = "pudans:ai:events";

    public static final String EVENT_STREAM_PREFIX = "pudans:ai:events:";

    public static final String EVENT_SEQUENCE_PREFIX = "pudans:ai:event-seq:";

    public static final int MAX_ATTEMPTS = 3;

    private static final String TASK_LOCK_PREFIX = "pudans:ai:task-lock:";

    private static final String TASK_HEARTBEAT_PREFIX = "pudans:ai:task-heartbeat:";

    private static final String CANCEL_PREFIX = "pudans:ai:cancel:";

    private GenerationTaskKeys() {}

    public static String lock(UUID taskId) {
        return TASK_LOCK_PREFIX + taskId;
    }

    public static String heartbeat(UUID taskId) {
        return TASK_HEARTBEAT_PREFIX + taskId;
    }

    public static String cancel(UUID scopeId) {
        return CANCEL_PREFIX + scopeId;
    }

    public static String eventStream(String streamId) {
        return EVENT_STREAM_PREFIX + streamId;
    }

    public static String eventSequence(String streamId) {
        return EVENT_SEQUENCE_PREFIX + streamId;
    }
}
