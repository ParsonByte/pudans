package cn.tolim.pudans.core.ai.memory.task;

import cn.tolim.pudans.core.ai.task.AiTask;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository.Conversation;
import org.jspecify.annotations.Nullable;

final class MemoryTaskPayloads {

    static final String CONVERSATION_ID = "conversationId";

    static final String TEAM_ID = "teamId";

    static final String AGENT_ID = "agentId";

    static final String AFTER_L1_ID = "afterL1Id";

    static final String LIMIT = "limit";

    static final int DEFAULT_L1_BATCH = 100;

    static final int MAX_L1_BATCH = 500;

    static final int PERSONA_THRESHOLD = 10;

    private MemoryTaskPayloads() {}

    static @Nullable Long conversationId(AiTask task) {
        var payload = longValue(task, CONVERSATION_ID);
        return payload != null && payload > 0 ? payload : task.conversationId();
    }

    static @Nullable Long longValue(AiTask task, String key) {
        var value = task.payload().get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text);
        }
        return null;
    }

    static @Nullable String text(AiTask task, String key) {
        var value = task.payload().get(key);
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    static @Nullable String teamId(AiTask task, @Nullable Conversation conversation) {
        return firstText(text(task, TEAM_ID), conversation == null ? null : conversation.teamId(), task.teamId());
    }

    static @Nullable String agentId(AiTask task, @Nullable Conversation conversation) {
        return firstText(text(task, AGENT_ID), conversation == null ? null : conversation.agentId(), task.agentId());
    }

    static long afterL1Id(AiTask task, @Nullable Conversation conversation) {
        if (conversation != null && conversation.l2CursorL1Id() != null) {
            return conversation.l2CursorL1Id();
        }
        var payload = longValue(task, AFTER_L1_ID);
        return payload == null || payload < 0 ? 0L : payload;
    }

    static int batchLimit(AiTask task) {
        var value = longValue(task, LIMIT);
        if (value == null || value <= 0) {
            return DEFAULT_L1_BATCH;
        }
        return (int) Math.min(value, MAX_L1_BATCH);
    }

    private static @Nullable String firstText(@Nullable String... values) {
        for (var value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
