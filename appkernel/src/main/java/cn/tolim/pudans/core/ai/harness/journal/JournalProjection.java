package cn.tolim.pudans.core.ai.harness.journal;

import cn.tolim.pudans.core.ai.harness.agent.AgentState;
import cn.tolim.pudans.core.ai.harness.agent.AgentStateCodec;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

public record JournalProjection(
        long asOfSeq,
        @Nullable AgentState state,
        Set<String> uncertainToolCalls,
        Map<String, String> toolNames,
        Set<String> activeRuns,
        @Nullable String checkpointRunId,
        @Nullable String checkpointTurnId) {

    public JournalProjection {
        if (asOfSeq < 0) {
            throw new JournalException("线程日志投影序号不能为负数。");
        }
        uncertainToolCalls = Set.copyOf(uncertainToolCalls == null ? Set.of() : uncertainToolCalls);
        toolNames = Map.copyOf(toolNames == null ? Map.of() : toolNames);
        activeRuns = Set.copyOf(activeRuns == null ? Set.of() : activeRuns);
    }

    public static Mono<JournalProjection> read(ThreadJournal journal) {
        return journal
                .head()
                .flatMap(head -> journal
                        .scan(0, head.seq())
                        .collectList()
                        .map(events -> project(head.seq(), events)));
    }

    private static JournalProjection project(long asOfSeq, List<JournalEvent> events) {
        var uncertain = new LinkedHashSet<String>();
        var toolNames = new LinkedHashMap<String, String>();
        var runs = new LinkedHashSet<String>();
        AgentState state = null;
        String checkpointRunId = null;
        String checkpointTurnId = null;
        for (var event : events) {
            switch (event.type()) {
                case STATE_CHECKPOINT -> {
                    state = restore(event);
                    checkpointRunId = event.runId();
                    checkpointTurnId = event.turnId();
                }
                case RUN_START -> runs.add(requireRunId(event));
                case RUN_END -> {
                    if (event.runId() != null) {
                        runs.remove(event.runId());
                    }
                }
                case TOOL_CALL_START -> {
                    var id = toolCallId(event);
                    uncertain.add(id);
                    toolNames.put(id, toolName(event));
                }
                case TOOL_RESULT_END -> {
                    var id = optionalToolCallId(event);
                    if (id != null) {
                        uncertain.remove(id);
                        toolNames.remove(id);
                    }
                }
                case RECOVERY_APPLIED -> {
                    for (var id : resolvedToolCallIds(event)) {
                        uncertain.remove(id);
                        toolNames.remove(id);
                    }
                    removeAll(runs, event.data().get("interruptedRuns"));
                }
                default -> {}
            }
        }
        return new JournalProjection(
                asOfSeq, state, uncertain, toolNames, runs, checkpointRunId, checkpointTurnId);
    }

    private static String toolName(JournalEvent event) {
        var value = event.data().get("toolName");
        return value instanceof String name && !name.isBlank() ? name : "unknown";
    }

    private static List<String> resolvedToolCallIds(JournalEvent event) {
        var value = event.data().get("resolvedToolCallIds");
        if (!(value instanceof List<?> items)) {
            return List.of();
        }
        return items.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    private static AgentState restore(JournalEvent event) {
        var value = event.data().get("stateJson");
        if (!(value instanceof String json) || json.isBlank()) {
            throw new JournalException("状态检查点缺少 stateJson。");
        }
        try {
            return AgentStateCodec.decode(json);
        } catch (RuntimeException error) {
            throw new JournalException("状态检查点无法恢复。", error);
        }
    }

    private static String requireRunId(JournalEvent event) {
        if (event.runId() == null || event.runId().isBlank()) {
            throw new JournalException("运行事件缺少运行标识。");
        }
        return event.runId();
    }

    private static String toolCallId(JournalEvent event) {
        var value = event.data().get("toolCallId");
        if (!(value instanceof String id) || id.isBlank()) {
            throw new JournalException("工具事件缺少调用标识。");
        }
        return id;
    }

    private static @Nullable String optionalToolCallId(JournalEvent event) {
        var value = event.data().get("toolCallId");
        return value instanceof String id && !id.isBlank() ? id : null;
    }

    private static void removeAll(Set<String> target, @Nullable Object value) {
        if (value instanceof List<?> items) {
            for (var item : items) {
                if (item instanceof String text) {
                    target.remove(text);
                }
            }
        }
    }
}
