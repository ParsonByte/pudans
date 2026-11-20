package cn.tolim.pudans.core.ai.harness.observability;

import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.harness.event.AgentEvent;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.jspecify.annotations.Nullable;

public final class HarnessObservationMapper {

    public static final String METRIC_AGENT_COMPLETED = "ai.harness.agent.completed";

    public static final String METRIC_MODEL_CALLS = "ai.harness.model.calls";

    public static final String METRIC_MODEL_TOKENS = "ai.harness.model.tokens";

    public static final String METRIC_TOOL_CALLS = "ai.harness.tool.calls";

    public static final String METRIC_TOOL_DURATION = "ai.harness.tool.duration";

    public static final String METRIC_TOOL_CONFIRMATIONS = "ai.harness.tool.confirmations";

    public static final String METRIC_TOOL_DENIED = "ai.harness.tool.denied";

    public static final String METRIC_MAX_ITERS = "ai.harness.run.max_iters";

    public static final String METRIC_STOP_REQUESTED = "ai.harness.run.stop_requested";

    private static final String ATTRIBUTE_OPERATION = "gen_ai.operation.name";

    private static final String ATTRIBUTE_AGENT_NAME = "gen_ai.agent.name";

    private static final String ATTRIBUTE_TOOL_NAME = "gen_ai.tool.name";

    private static final String ATTRIBUTE_TOOL_CALL_ID = "gen_ai.tool.call.id";

    private static final String ATTRIBUTE_ITERATION = "harness.model.iteration";

    private static final String ATTRIBUTE_FINISH_REASON = "gen_ai.response.finish_reason";

    private static final String TAG_STATUS = "status";

    private static final String TAG_TOOL_NAME = "tool_name";

    private static final String TAG_MODEL = "model";

    private static final String TAG_DIRECTION = "direction";

    private static final String TAG_APPROVED = "approved";

    private static final String TAG_FINISH_REASON = "finish_reason";

    private static final String TAG_STAGE = "stage";

    private static final String TAG_MAX_ITERS = "max_iters";

    private static final String UNKNOWN = "unknown";

    private final HarnessObservability observability;

    private final Clock clock;

    private final ConcurrentMap<String, HarnessSpan> agentSpans = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Boolean> completedRuns = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Boolean> stoppedRuns = new ConcurrentHashMap<>();

    private final ConcurrentMap<ModelCallKey, HarnessSpan> modelSpans = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, HarnessSpan> toolSpans = new ConcurrentHashMap<>();

    public HarnessObservationMapper(HarnessObservability observability) {
        this(observability, Clock.systemUTC());
    }

    HarnessObservationMapper(HarnessObservability observability, Clock clock) {
        this.observability = Objects.requireNonNull(observability, "观测出口不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    public void onEvent(AgentEvent event) {
        Objects.requireNonNull(event, "Agent 事件不能为空");
        switch (event) {
            case AgentEvent.AgentStart value -> startAgent(value);
            case AgentEvent.AgentResult value -> completedRuns.put(value.runId(), Boolean.TRUE);
            case AgentEvent.RequestStop value -> {
                stoppedRuns.put(value.runId(), Boolean.TRUE);
                metric(METRIC_STOP_REQUESTED, HarnessMetric.Kind.COUNTER, 1, Map.of());
            }
            case AgentEvent.AgentEnd value -> endAgent(value);
            case AgentEvent.ModelCallStart value -> startModelCall(value);
            case AgentEvent.ModelCallEnd value -> endModelCall(value);
            case AgentEvent.ToolCallStart value -> startToolCall(value);
            case AgentEvent.ToolResultEnd value -> endToolCall(value);
            case AgentEvent.RequireUserConfirm value -> metric(
                    METRIC_TOOL_CONFIRMATIONS,
                    HarnessMetric.Kind.COUNTER,
                    1,
                    Map.of(TAG_TOOL_NAME, value.name(), TAG_STAGE, "requested"));
            case AgentEvent.UserConfirmResult value -> metric(
                    METRIC_TOOL_CONFIRMATIONS,
                    HarnessMetric.Kind.COUNTER,
                    1,
                    Map.of(TAG_STAGE, "resolved", TAG_APPROVED, Boolean.toString(value.approved())));
            case AgentEvent.AllToolsDenied value -> metric(
                    METRIC_TOOL_DENIED, HarnessMetric.Kind.COUNTER, 1, Map.of());
            case AgentEvent.ExceedMaxIters value -> metric(
                    METRIC_MAX_ITERS,
                    HarnessMetric.Kind.COUNTER,
                    1,
                    Map.of(TAG_MAX_ITERS, Integer.toString(value.maxIters())));
            default -> {}
        }
    }

    public void recordUsage(@Nullable String model, AiTokenUsage usage) {
        if (usage == null) {
            return;
        }
        token(model, "input", usage.inputTokens());
        token(model, "output", usage.outputTokens());
        token(model, "cache_read", usage.cacheReadTokens());
        token(model, "cache_write", usage.cacheWriteTokens());
    }

    private void startAgent(AgentEvent.AgentStart event) {
        var span = HarnessSpan.started(
                "agent:" + event.runId(),
                null,
                HarnessSpanKind.AGENT,
                "invoke_agent " + event.agentName(),
                event.runId(),
                Map.of(ATTRIBUTE_OPERATION, "invoke_agent", ATTRIBUTE_AGENT_NAME, event.agentName()),
                clock.instant());
        if (agentSpans.putIfAbsent(event.runId(), span) == null) {
            observability.spanStarted(span);
        }
    }

    private void endAgent(AgentEvent.AgentEnd event) {
        var span = agentSpans.remove(event.runId());
        if (span == null) {
            return;
        }
        var status = stoppedRuns.remove(event.runId()) != null
                ? HarnessSpanStatus.CANCELLED
                : completedRuns.remove(event.runId()) != null
                        ? HarnessSpanStatus.OK
                        : HarnessSpanStatus.ERROR;
        observability.spanEnded(span.ended(status, null, clock.instant()));
        metric(
                METRIC_AGENT_COMPLETED,
                HarnessMetric.Kind.COUNTER,
                1,
                Map.of(TAG_STATUS, status.name(), ATTRIBUTE_AGENT_NAME, event.agentName()));
    }

    private void startModelCall(AgentEvent.ModelCallStart event) {
        var parent = agentSpans.get(event.runId());
        var key = new ModelCallKey(event.runId(), event.iteration());
        var span = HarnessSpan.started(
                "model:" + event.runId() + ":" + event.iteration(),
                parent == null ? null : parent.spanId(),
                HarnessSpanKind.MODEL,
                "chat",
                event.runId(),
                Map.of(
                        ATTRIBUTE_OPERATION,
                        "chat",
                        ATTRIBUTE_ITERATION,
                        Integer.toString(event.iteration())),
                clock.instant());
        if (modelSpans.putIfAbsent(key, span) == null) {
            observability.spanStarted(span);
        }
    }

    private void endModelCall(AgentEvent.ModelCallEnd event) {
        var span = modelSpans.remove(new ModelCallKey(event.runId(), event.iteration()));
        if (span == null) {
            return;
        }
        var attributes = new LinkedHashMap<>(span.attributes());
        var finishReason = event.finishReason();
        if (finishReason != null && !finishReason.isBlank()) {
            attributes.put(ATTRIBUTE_FINISH_REASON, finishReason);
        }
        observability.spanEnded(new HarnessSpan(
                span.spanId(),
                span.parentSpanId(),
                span.kind(),
                span.name(),
                span.runId(),
                attributes,
                span.startedAt(),
                clock.instant(),
                HarnessSpanStatus.OK,
                null));
        metric(
                METRIC_MODEL_CALLS,
                HarnessMetric.Kind.COUNTER,
                1,
                Map.of(
                        TAG_FINISH_REASON,
                        finishReason == null || finishReason.isBlank() ? UNKNOWN : finishReason,
                        TAG_STATUS,
                        HarnessSpanStatus.OK.name()));
    }

    private void startToolCall(AgentEvent.ToolCallStart event) {
        var parent = agentSpans.get(event.runId());
        var span = HarnessSpan.started(
                "tool:" + event.id(),
                parent == null ? null : parent.spanId(),
                HarnessSpanKind.TOOL,
                "execute_tool " + event.name(),
                event.runId(),
                Map.of(
                        ATTRIBUTE_OPERATION,
                        "execute_tool",
                        ATTRIBUTE_TOOL_NAME,
                        event.name(),
                        ATTRIBUTE_TOOL_CALL_ID,
                        event.id()),
                clock.instant());
        if (toolSpans.putIfAbsent(event.id(), span) == null) {
            observability.spanStarted(span);
        }
    }

    private void endToolCall(AgentEvent.ToolResultEnd event) {
        var status = event.error() ? HarnessSpanStatus.ERROR : HarnessSpanStatus.OK;
        var span = toolSpans.remove(event.id());
        if (span != null) {
            var endedAt = clock.instant();
            observability.spanEnded(
                    span.ended(status, event.error() ? "tool_error" : null, endedAt));
            metric(
                    METRIC_TOOL_DURATION,
                    HarnessMetric.Kind.TIMER,
                    Duration.between(span.startedAt(), endedAt).toMillis(),
                    Map.of(TAG_TOOL_NAME, event.name()));
        }
        metric(
                METRIC_TOOL_CALLS,
                HarnessMetric.Kind.COUNTER,
                1,
                Map.of(TAG_TOOL_NAME, event.name(), TAG_STATUS, status.name()));
    }

    private void token(@Nullable String model, String direction, @Nullable Integer amount) {
        if (amount == null || amount <= 0) {
            return;
        }
        metric(
                METRIC_MODEL_TOKENS,
                HarnessMetric.Kind.COUNTER,
                amount,
                Map.of(
                        TAG_MODEL,
                        model == null || model.isBlank() ? UNKNOWN : model,
                        TAG_DIRECTION,
                        direction));
    }

    private void metric(
            String name, HarnessMetric.Kind kind, double value, Map<String, String> tags) {
        observability.metric(new HarnessMetric(name, kind, value, tags));
    }

    private record ModelCallKey(String runId, int iteration) {}
}