package cn.tolim.pudans.core.ai.harness.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.harness.event.AgentEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

class HarnessObservationMapperTest {

    @Test
    void mapsAgentModelAndToolLifecycleToSpansAndMetrics() {
        var sink = new InMemoryHarnessObservability();
        var mapper = new HarnessObservationMapper(sink);

        mapper.onEvent(new AgentEvent.AgentStart("run-1", "assistant"));
        mapper.onEvent(new AgentEvent.ModelCallStart("run-1", 0));
        mapper.onEvent(new AgentEvent.ModelCallEnd("run-1", 0, "STOP"));
        mapper.onEvent(new AgentEvent.ToolCallStart("run-1", "call-1", "echo", "{}"));
        mapper.onEvent(new AgentEvent.ToolResultEnd("run-1", "call-1", "echo", "ok", false));
        mapper.onEvent(new AgentEvent.AgentResult("run-1", "done"));
        mapper.onEvent(new AgentEvent.AgentEnd("run-1", "assistant"));

        assertThat(sink.spans()).hasSize(6);
        var agent = endedSpan(sink.spans(), "invoke_agent assistant");
        var model = endedSpan(sink.spans(), "chat");
        var tool = endedSpan(sink.spans(), "execute_tool echo");
        assertThat(agent.status()).isEqualTo(HarnessSpanStatus.OK);
        assertThat(agent.kind()).isEqualTo(HarnessSpanKind.AGENT);
        assertThat(model.parentSpanId()).isEqualTo(agent.spanId());
        assertThat(model.attributes()).containsEntry("gen_ai.response.finish_reason", "STOP");
        assertThat(tool.parentSpanId()).isEqualTo(agent.spanId());
        assertThat(tool.endedAt()).isNotNull();
        assertThat(sink.metrics())
                .extracting(HarnessMetric::name)
                .contains(
                        HarnessObservationMapper.METRIC_AGENT_COMPLETED,
                        HarnessObservationMapper.METRIC_MODEL_CALLS,
                        HarnessObservationMapper.METRIC_TOOL_CALLS,
                        HarnessObservationMapper.METRIC_TOOL_DURATION);
    }

    @Test
    void mapsTokenUsageToDirectionCounters() {
        var sink = new InMemoryHarnessObservability();
        var mapper = new HarnessObservationMapper(sink);

        mapper.recordUsage("gpt-4", new AiTokenUsage(10, 5, 2, 3));

        assertThat(sink.metrics())
                .filteredOn(metric -> metric.name().equals(HarnessObservationMapper.METRIC_MODEL_TOKENS))
                .extracting(metric -> metric.tags().get("direction"), HarnessMetric::value)
                .containsExactlyInAnyOrder(
                        tuple("input", 10.0),
                        tuple("output", 5.0),
                        tuple("cache_read", 2.0),
                        tuple("cache_write", 3.0));
    }

    @Test
    void skipsMissingAndNonPositiveTokenCounts() {
        var sink = new InMemoryHarnessObservability();
        var mapper = new HarnessObservationMapper(sink);

        mapper.recordUsage(null, new AiTokenUsage(null, 0, -1, null));

        assertThat(sink.metrics()).isEmpty();
    }

    @Test
    void marksCancelledAgentSpan() {
        var sink = new InMemoryHarnessObservability();
        var mapper = new HarnessObservationMapper(sink);

        mapper.onEvent(new AgentEvent.AgentStart("run-2", "worker"));
        mapper.onEvent(new AgentEvent.RequestStop("run-2"));
        mapper.onEvent(new AgentEvent.AgentEnd("run-2", "worker"));

        var agent = endedSpan(sink.spans(), "invoke_agent worker");
        assertThat(agent.status()).isEqualTo(HarnessSpanStatus.CANCELLED);
        assertThat(sink.metrics())
                .extracting(HarnessMetric::name)
                .contains(HarnessObservationMapper.METRIC_STOP_REQUESTED);
    }

    @Test
    void marksFailedAgentAndToolSpans() {
        var sink = new InMemoryHarnessObservability();
        var mapper = new HarnessObservationMapper(sink);

        mapper.onEvent(new AgentEvent.AgentStart("run-3", "broken"));
        mapper.onEvent(new AgentEvent.ToolCallStart("run-3", "call-2", "write", "{}"));
        mapper.onEvent(new AgentEvent.ToolResultEnd("run-3", "call-2", "write", "failed", true));
        mapper.onEvent(new AgentEvent.AgentEnd("run-3", "broken"));

        var agent = endedSpan(sink.spans(), "invoke_agent broken");
        var tool = endedSpan(sink.spans(), "execute_tool write");
        assertThat(agent.status()).isEqualTo(HarnessSpanStatus.ERROR);
        assertThat(tool.status()).isEqualTo(HarnessSpanStatus.ERROR);
        assertThat(tool.errorType()).isEqualTo("tool_error");
    }

    @Test
    void noopSinkAcceptsObservations() {
        var mapper = new HarnessObservationMapper(NoopHarnessObservability.INSTANCE);

        mapper.onEvent(new AgentEvent.AgentStart("run-4", "noop"));
        mapper.onEvent(new AgentEvent.AgentEnd("run-4", "noop"));
        mapper.recordUsage("gpt-4", new AiTokenUsage(1, 1, null, null));
    }

    private static HarnessSpan endedSpan(List<HarnessSpan> spans, String name) {
        return spans.stream()
                .filter(span -> span.name().equals(name) && span.endedAt() != null)
                .findFirst()
                .orElseThrow();
    }
}