package cn.tolim.pudans.core.infra.r2dbc.ai.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.r2dbc.spi.Row;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiGatewaySessionRepositoryTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    Row row;

    @Test
    void mapsAGatewaySessionRow() {
        when(row.get("id", Long.class)).thenReturn(41L);
        when(row.get("space_id", String.class)).thenReturn("default");
        when(row.get("user_id", String.class)).thenReturn("user-1");
        when(row.get("agent_source", String.class)).thenReturn("claude-code");
        when(row.get("session_key", String.class)).thenReturn("session-1");
        when(row.get("session_id", String.class)).thenReturn("native-1");
        when(row.get("team_id", String.class)).thenReturn("team-a");
        when(row.get("agent_id", String.class)).thenReturn("agent-a");
        when(row.get("task_id", String.class)).thenReturn("task-1");
        when(row.get("conversation_id", Long.class)).thenReturn(11L);
        when(row.get("status", String.class)).thenReturn("ACTIVE");
        when(row.get("created_at", Instant.class)).thenReturn(CREATED_AT);
        when(row.get("updated_at", Instant.class)).thenReturn(CREATED_AT);

        var session = AiGatewaySessionRepository.mapGatewaySession(row);

        assertThat(session.id()).isEqualTo(41L);
        assertThat(session.spaceId()).isEqualTo("default");
        assertThat(session.userId()).isEqualTo("user-1");
        assertThat(session.agentSource()).isEqualTo("claude-code");
        assertThat(session.sessionKey()).isEqualTo("session-1");
        assertThat(session.sessionId()).isEqualTo("native-1");
        assertThat(session.teamId()).isEqualTo("team-a");
        assertThat(session.agentId()).isEqualTo("agent-a");
        assertThat(session.taskId()).isEqualTo("task-1");
        assertThat(session.conversationId()).isEqualTo(11L);
        assertThat(session.status()).isEqualTo("ACTIVE");
        assertThat(session.createdAt()).isEqualTo(CREATED_AT);
        assertThat(session.updatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void rejectsASessionWithoutAnAgent() {
        assertThatThrownBy(() -> new AiGatewaySessionRepository.NewGatewaySession(
                        "default", "user-1", "claude-code", "session-1", null, "team-a", " ", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agentId");
    }
}
