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
class AiConversationRepositoryTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    Row row;

    @Test
    void mapsAConversationRow() {
        when(row.get("id", Long.class)).thenReturn(11L);
        when(row.get("space_id", String.class)).thenReturn("default");
        when(row.get("user_id", String.class)).thenReturn("user-1");
        when(row.get("team_id", String.class)).thenReturn("team-a");
        when(row.get("agent_id", String.class)).thenReturn("agent-a");
        when(row.get("agent_source", String.class)).thenReturn("pudans-chat");
        when(row.get("session_key", String.class)).thenReturn("session-1");
        when(row.get("task_id", String.class)).thenReturn("task-1");
        when(row.get("title", String.class)).thenReturn("标题");
        when(row.get("mode_code", String.class)).thenReturn("fast");
        when(row.get("route_group", String.class)).thenReturn("fast-group");
        when(row.get("status", String.class)).thenReturn("ACTIVE");
        when(row.get("l1_cursor_message_id", Long.class)).thenReturn(31L);
        when(row.get("l2_cursor_l1_id", Long.class)).thenReturn(41L);
        when(row.get("pending_count", Integer.class)).thenReturn(4);
        when(row.get("pipeline_version", Long.class)).thenReturn(2L);
        when(row.get("last_activity_at", Instant.class)).thenReturn(CREATED_AT);
        when(row.get("created_at", Instant.class)).thenReturn(CREATED_AT);
        when(row.get("updated_at", Instant.class)).thenReturn(CREATED_AT);

        var conversation = AiConversationRepository.mapConversation(row);

        assertThat(conversation.id()).isEqualTo(11L);
        assertThat(conversation.spaceId()).isEqualTo("default");
        assertThat(conversation.userId()).isEqualTo("user-1");
        assertThat(conversation.teamId()).isEqualTo("team-a");
        assertThat(conversation.agentId()).isEqualTo("agent-a");
        assertThat(conversation.agentSource()).isEqualTo("pudans-chat");
        assertThat(conversation.sessionKey()).isEqualTo("session-1");
        assertThat(conversation.taskId()).isEqualTo("task-1");
        assertThat(conversation.title()).isEqualTo("标题");
        assertThat(conversation.modeCode()).isEqualTo("fast");
        assertThat(conversation.routeGroup()).isEqualTo("fast-group");
        assertThat(conversation.status()).isEqualTo("ACTIVE");
        assertThat(conversation.l1CursorMessageId()).isEqualTo(31L);
        assertThat(conversation.l2CursorL1Id()).isEqualTo(41L);
        assertThat(conversation.pendingCount()).isEqualTo(4);
        assertThat(conversation.pipelineVersion()).isEqualTo(2L);
        assertThat(conversation.lastActivityAt()).isEqualTo(CREATED_AT);
        assertThat(conversation.createdAt()).isEqualTo(CREATED_AT);
        assertThat(conversation.updatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void mapsThePendingCountOfAConversationRow() {
        when(row.get("pending_count", Integer.class)).thenReturn(4);

        assertThat(AiConversationRepository.mapPendingCount(row)).isEqualTo(4);
    }

    @Test
    void rejectsAConversationWithoutASessionKey() {
        assertThatThrownBy(() -> new AiConversationRepository.NewConversation(
                        "default", "user-1", "team-a", "agent-a", "pudans-chat", " ", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sessionKey");
    }
}
