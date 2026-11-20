package cn.tolim.pudans.core.ai.harness.journal;

import cn.tolim.pudans.core.ai.harness.agent.AgentState;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

public final class JournalEventSink {

    private final @Nullable ThreadJournal journal;

    private final Sinks.Many<JournalEventDraft> queue =
            Sinks.many().unicast().onBackpressureBuffer();

    private final Sinks.Empty<Void> done = Sinks.empty();

    public JournalEventSink(@Nullable ThreadJournal journal) {
        this.journal = journal;
        if (journal == null) {
            queue.tryEmitComplete();
            done.tryEmitEmpty();
            return;
        }
        queue.asFlux()
                .concatMap(draft -> journal.append(draft).then())
                .doOnError(done::tryEmitError)
                .doOnComplete(done::tryEmitEmpty)
                .subscribe();
    }

    public static JournalEventSink disabled() {
        return new JournalEventSink(null);
    }

    public void record(JournalEventType type, @Nullable String runId, Object payload) {
        Objects.requireNonNull(type, "事件类型不能为空");
        Objects.requireNonNull(payload, "事件负载不能为空");
        if (journal == null) {
            return;
        }
        var result = queue.tryEmitNext(
                new JournalEventDraft(
                        UUID.randomUUID().toString(),
                        Instant.now(),
                        type,
                        runId,
                        null,
                        true,
                        payload));
        if (result.isFailure()) {
            throw new IllegalStateException("线程日志已结束，无法继续写入。");
        }
    }

    public void checkpoint(AgentState state, String reason) {
        record(JournalEventType.STATE_CHECKPOINT, null, ThreadCheckpoints.payload(state, reason));
    }

    public void modelCallStart(String runId, int iteration) {
        record(JournalEventType.MODEL_CALL_START, runId, Map.of("iteration", iteration));
    }

    public void modelCallEnd(String runId, int iteration, @Nullable String finishReason) {
        record(
                JournalEventType.MODEL_CALL_END,
                runId,
                Map.of("iteration", iteration, "finishReason", finishReason == null ? "" : finishReason));
    }

    public void toolCallStart(
            String runId, String toolCallId, String toolName, String arguments) {
        record(
                JournalEventType.TOOL_CALL_START,
                runId,
                Map.of("toolCallId", toolCallId, "toolName", toolName, "arguments", arguments));
    }

    public void toolResultEnd(
            String runId, String toolCallId, String toolName, String output, boolean error) {
        record(
                JournalEventType.TOOL_RESULT_END,
                runId,
                Map.of(
                        "toolCallId", toolCallId,
                        "toolName", toolName,
                        "output", output,
                        "error", error));
    }

    public void toolDecision(String runId, String toolCallId, String decision) {
        record(
                JournalEventType.TOOL_DECISION,
                runId,
                Map.of("toolCallId", toolCallId, "decision", decision));
    }

    public Mono<Void> flush() {
        queue.tryEmitComplete();
        return done.asMono();
    }
}