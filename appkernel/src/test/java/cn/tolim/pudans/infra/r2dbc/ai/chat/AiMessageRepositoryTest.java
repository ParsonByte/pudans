package cn.tolim.pudans.infra.r2dbc.ai.chat;

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
class AiMessageRepositoryTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    Row row;

    @Test
    void mapsAMessageRow() {
        when(row.get("id", Long.class)).thenReturn(21L);
        when(row.get("conversation_id", Long.class)).thenReturn(11L);
        when(row.get("space_id", String.class)).thenReturn("default");
        when(row.get("user_id", String.class)).thenReturn("user-1");
        when(row.get("team_id", String.class)).thenReturn("team-a");
        when(row.get("agent_id", String.class)).thenReturn("agent-a");
        when(row.get("session_id", String.class)).thenReturn("session-1");
        when(row.get("task_id", String.class)).thenReturn("task-1");
        when(row.get("request_id", String.class)).thenReturn("req-1");
        when(row.get("turn_index", Integer.class)).thenReturn(2);
        when(row.get("message_index", Integer.class)).thenReturn(1);
        when(row.get("role", String.class)).thenReturn("assistant");
        when(row.get("content", String.class)).thenReturn("飞书同步需要配置应用凭证。");
        when(row.get("content_type", String.class)).thenReturn("TEXT");
        when(row.get("token_count", Integer.class)).thenReturn(128);
        when(row.get("model_code", String.class)).thenReturn("gpt-4o-mini");
        when(row.get("created_at", Instant.class)).thenReturn(CREATED_AT);

        var message = AiMessageRepository.mapMessage(row);

        assertThat(message.id()).isEqualTo(21L);
        assertThat(message.conversationId()).isEqualTo(11L);
        assertThat(message.spaceId()).isEqualTo("default");
        assertThat(message.userId()).isEqualTo("user-1");
        assertThat(message.teamId()).isEqualTo("team-a");
        assertThat(message.agentId()).isEqualTo("agent-a");
        assertThat(message.sessionId()).isEqualTo("session-1");
        assertThat(message.taskId()).isEqualTo("task-1");
        assertThat(message.requestId()).isEqualTo("req-1");
        assertThat(message.turnIndex()).isEqualTo(2);
        assertThat(message.messageIndex()).isEqualTo(1);
        assertThat(message.role()).isEqualTo("assistant");
        assertThat(message.content()).isEqualTo("飞书同步需要配置应用凭证。");
        assertThat(message.contentType()).isEqualTo("TEXT");
        assertThat(message.tokenCount()).isEqualTo(128);
        assertThat(message.modelCode()).isEqualTo("gpt-4o-mini");
        assertThat(message.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void rejectsANegativeMessageIndex() {
        assertThatThrownBy(() -> new AiMessageRepository.NewMessage(
                        11L,
                        "default",
                        "user-1",
                        "team-a",
                        "agent-a",
                        null,
                        null,
                        null,
                        1,
                        -1,
                        "user",
                        "内容",
                        "TEXT",
                        null,
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("messageIndex");
    }
}
