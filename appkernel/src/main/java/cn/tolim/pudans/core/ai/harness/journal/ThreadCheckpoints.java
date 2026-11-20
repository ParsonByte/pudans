package cn.tolim.pudans.core.ai.harness.journal;

import cn.tolim.pudans.core.ai.harness.agent.AgentState;
import cn.tolim.pudans.core.ai.harness.agent.AgentStateCodec;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

public final class ThreadCheckpoints {

    private ThreadCheckpoints() {}

    public static Mono<JournalEvent> initialize(ThreadJournal journal, AgentState state) {
        Objects.requireNonNull(journal, "线程日志不能为空");
        Objects.requireNonNull(state, "Agent 状态不能为空");
        var draft = JournalEventDraft.of(
                JournalEventType.STATE_CHECKPOINT, null, payload(state, "baseline"));
        return journal.append(0, List.of(draft)).map(events -> events.get(0));
    }

    public static Mono<JournalEvent> checkpoint(
            ThreadJournal journal, AgentState state, String reason) {
        Objects.requireNonNull(journal, "线程日志不能为空");
        Objects.requireNonNull(state, "Agent 状态不能为空");
        if (reason == null || reason.isBlank()) {
            return Mono.error(new IllegalArgumentException("状态检查点原因不能为空。"));
        }
        return journal.append(
                JournalEventDraft.of(JournalEventType.STATE_CHECKPOINT, null, payload(state, reason)));
    }

    static Map<String, Object> payload(AgentState state, String reason) {
        return Map.of("stateJson", AgentStateCodec.encode(state), "reason", reason);
    }
}