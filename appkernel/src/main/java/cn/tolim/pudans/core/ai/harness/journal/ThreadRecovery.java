package cn.tolim.pudans.core.ai.harness.journal;

import dev.langchain4j.data.message.ToolExecutionResultMessage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import reactor.core.publisher.Mono;

public final class ThreadRecovery {

    private ThreadRecovery() {}

    public record ToolOutcome(String toolCallId, String toolName, String output, boolean error) {

        public ToolOutcome {
            if (toolCallId == null || toolCallId.isBlank()) {
                throw new IllegalArgumentException("工具调用标识不能为空。");
            }
            if (toolName == null || toolName.isBlank()) {
                throw new IllegalArgumentException("工具名称不能为空。");
            }
            output = output == null ? "" : output;
        }
    }

    public static Mono<JournalProjection> reconcile(
            ThreadJournal journal, Map<String, ToolOutcome> outcomes, String reason) {
        Objects.requireNonNull(journal, "线程日志不能为空");
        Objects.requireNonNull(outcomes, "工具结果不能为空");
        if (reason == null || reason.isBlank()) {
            return Mono.error(new IllegalArgumentException("对账原因不能为空。"));
        }
        return JournalProjection.read(journal).flatMap(projection -> {
            if (projection.state() == null) {
                return Mono.error(new JournalException("线程日志没有可恢复的状态基线。"));
            }
            if (!projection.uncertainToolCalls().equals(outcomes.keySet())) {
                return Mono.error(new IllegalArgumentException("必须提供全部未确定工具调用的结果。"));
            }
            var state = projection.state();
            var drafts = new ArrayList<JournalEventDraft>();
            for (var outcome : outcomes.values()) {
                state.addMessage(ToolExecutionResultMessage.builder()
                        .id(outcome.toolCallId())
                        .toolName(outcome.toolName())
                        .text(outcome.output())
                        .isError(outcome.error())
                        .build());
                drafts.add(draft(
                        JournalEventType.TOOL_RESULT_END,
                        projection,
                        Map.of(
                                "toolCallId", outcome.toolCallId(),
                                "toolName", outcome.toolName(),
                                "output", outcome.output(),
                                "error", outcome.error(),
                                "reconciled", true)));
            }
            drafts.add(draft(
                    JournalEventType.RECOVERY_APPLIED,
                    projection,
                    Map.of(
                            "resolvedToolCallIds", List.copyOf(outcomes.keySet()),
                            "interruptedRuns", List.copyOf(projection.activeRuns()),
                            "reason", reason)));
            drafts.add(draft(
                    JournalEventType.STATE_CHECKPOINT,
                    projection,
                    ThreadCheckpoints.payload(state, "recovery")));
            return journal
                    .append(projection.asOfSeq(), drafts)
                    .then(JournalProjection.read(journal));
        });
    }

    private static JournalEventDraft draft(
            JournalEventType type, JournalProjection projection, Object payload) {
        return new JournalEventDraft(
                UUID.randomUUID().toString(),
                Instant.now(),
                type,
                projection.checkpointRunId(),
                projection.checkpointTurnId(),
                true,
                payload);
    }
}