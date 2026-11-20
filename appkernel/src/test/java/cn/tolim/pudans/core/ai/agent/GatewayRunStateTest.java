package cn.tolim.pudans.core.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.core.ai.gateway.AiChatResponse;
import cn.tolim.pudans.core.ai.gateway.AiGatewayMeta;
import cn.tolim.pudans.core.ai.gateway.AiGatewayResult;
import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class GatewayRunStateTest {

    @Test
    void accumulatesTokenUsageAndPointsAcrossCalls() {
        var state = new GatewayRunState();

        state.recordUsage(new AiTokenUsage(1, 2, null, null), BigDecimal.ONE, "PROVIDER");
        state.recordUsage(new AiTokenUsage(3, null, 4, null), BigDecimal.ONE, null);

        assertThat(state.usage()).isEqualTo(new AiTokenUsage(4, 2, 4, null));
        assertThat(state.points()).isEqualByComparingTo("2");
        assertThat(state.billingSource()).isEqualTo("PROVIDER");
    }

    @Test
    void accumulatesTokenUsageFromGatewayResults() {
        var state = new GatewayRunState();

        state.record(result(new AiTokenUsage(1, 2, null, null)));
        state.record(result(new AiTokenUsage(3, 4, 5, 6)));

        assertThat(state.usage()).isEqualTo(new AiTokenUsage(4, 6, 5, 6));
        assertThat(state.meta()).isNotNull();
    }

    private static AiGatewayResult result(AiTokenUsage usage) {
        return new AiGatewayResult(
                new AiChatResponse(
                        "resp-1",
                        "gpt-test",
                        new AiChatMessage("assistant", List.of(new AiContentPart.Text("回答", null))),
                        usage,
                        "stop"),
                new AiGatewayMeta(
                        "req-1",
                        7L,
                        "fast",
                        "fast-group",
                        "provider-a",
                        "account-a",
                        "gpt-test",
                        null,
                        "team-a",
                        "agent-a",
                        "session-1"),
                usage,
                BigDecimal.ONE,
                "PROVIDER");
    }
}