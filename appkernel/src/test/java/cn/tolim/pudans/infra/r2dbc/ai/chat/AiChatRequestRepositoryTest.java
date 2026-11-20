package cn.tolim.pudans.infra.r2dbc.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.r2dbc.spi.Row;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiChatRequestRepositoryTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    private static final Instant COMPLETED_AT = Instant.parse("2026-01-01T00:00:03Z");

    @Mock
    Row row;

    @Test
    void mapsAChatRequestRow() {
        when(row.get("id", Long.class)).thenReturn(31L);
        when(row.get("request_id", String.class)).thenReturn("req-1");
        when(row.get("space_id", String.class)).thenReturn("default");
        when(row.get("user_id", String.class)).thenReturn("user-1");
        when(row.get("agent_source", String.class)).thenReturn("pudans-chat");
        when(row.get("team_id", String.class)).thenReturn("team-a");
        when(row.get("agent_id", String.class)).thenReturn("agent-a");
        when(row.get("session_key", String.class)).thenReturn("session-1");
        when(row.get("conversation_id", Long.class)).thenReturn(11L);
        when(row.get("idempotency_key", String.class)).thenReturn("idem-1");
        when(row.get("mode_code", String.class)).thenReturn("fast");
        when(row.get("route_group", String.class)).thenReturn("fast-group");
        when(row.get("selected_provider", String.class)).thenReturn("openai-primary");
        when(row.get("selected_account", String.class)).thenReturn("openai-primary-main");
        when(row.get("selected_model", String.class)).thenReturn("gpt-4o-mini");
        when(row.get("tier", String.class)).thenReturn("CHEAP");
        when(row.get("status", String.class)).thenReturn("SUCCEEDED");
        when(row.get("error_code", String.class)).thenReturn(null);
        when(row.get("input_tokens", Integer.class)).thenReturn(1200);
        when(row.get("cache_tokens", Integer.class)).thenReturn(200);
        when(row.get("output_tokens", Integer.class)).thenReturn(300);
        when(row.get("total_tokens", Integer.class)).thenReturn(1700);
        when(row.get("usage_units", Long.class)).thenReturn(2L);
        when(row.get("plan_id", String.class)).thenReturn("standard");
        when(row.get("billing_scope", String.class)).thenReturn("USER");
        when(row.get("billing_source", String.class)).thenReturn("PROVIDER");
        when(row.get("billing_status", String.class)).thenReturn("SETTLED");
        when(row.get("latency_ms", Integer.class)).thenReturn(1800);
        when(row.get("ttft_ms", Integer.class)).thenReturn(420);
        when(row.get("tps", BigDecimal.class)).thenReturn(new BigDecimal("31.50"));
        when(row.get("usage_source", String.class)).thenReturn("UPSTREAM");
        when(row.get("upstream_request_id", String.class)).thenReturn("upstream-1");
        when(row.get("created_at", Instant.class)).thenReturn(CREATED_AT);
        when(row.get("completed_at", Instant.class)).thenReturn(COMPLETED_AT);

        var request = AiChatRequestRepository.mapChatRequest(row);

        assertThat(request.id()).isEqualTo(31L);
        assertThat(request.requestId()).isEqualTo("req-1");
        assertThat(request.spaceId()).isEqualTo("default");
        assertThat(request.userId()).isEqualTo("user-1");
        assertThat(request.agentSource()).isEqualTo("pudans-chat");
        assertThat(request.teamId()).isEqualTo("team-a");
        assertThat(request.agentId()).isEqualTo("agent-a");
        assertThat(request.sessionKey()).isEqualTo("session-1");
        assertThat(request.conversationId()).isEqualTo(11L);
        assertThat(request.idempotencyKey()).isEqualTo("idem-1");
        assertThat(request.modeCode()).isEqualTo("fast");
        assertThat(request.routeGroup()).isEqualTo("fast-group");
        assertThat(request.selectedProvider()).isEqualTo("openai-primary");
        assertThat(request.selectedAccount()).isEqualTo("openai-primary-main");
        assertThat(request.selectedModel()).isEqualTo("gpt-4o-mini");
        assertThat(request.tier()).isEqualTo("CHEAP");
        assertThat(request.status()).isEqualTo("SUCCEEDED");
        assertThat(request.errorCode()).isNull();
        assertThat(request.inputTokens()).isEqualTo(1200);
        assertThat(request.cacheTokens()).isEqualTo(200);
        assertThat(request.outputTokens()).isEqualTo(300);
        assertThat(request.totalTokens()).isEqualTo(1700);
        assertThat(request.units()).isEqualTo(2L);
        assertThat(request.planId()).isEqualTo("standard");
        assertThat(request.billingScope()).isEqualTo("USER");
        assertThat(request.billingSource()).isEqualTo("PROVIDER");
        assertThat(request.billingStatus()).isEqualTo("SETTLED");
        assertThat(request.latencyMs()).isEqualTo(1800);
        assertThat(request.ttftMs()).isEqualTo(420);
        assertThat(request.tps()).isEqualByComparingTo("31.50");
        assertThat(request.usageSource()).isEqualTo("UPSTREAM");
        assertThat(request.upstreamRequestId()).isEqualTo("upstream-1");
        assertThat(request.createdAt()).isEqualTo(CREATED_AT);
        assertThat(request.completedAt()).isEqualTo(COMPLETED_AT);
    }

    @Test
    void rejectsACompletionWithoutARequestId() {
        assertThatThrownBy(() -> new AiChatRequestRepository.ChatCompletion(
                        " ", "SUCCEEDED", null, null, null, null, null, null, null, "SETTLED",
                        null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requestId");
    }
}
