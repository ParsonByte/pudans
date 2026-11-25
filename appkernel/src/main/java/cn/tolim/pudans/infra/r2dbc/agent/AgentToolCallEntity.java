package cn.tolim.pudans.infra.r2dbc.agent;

import cn.tolim.pudans.contract.agent.AgentToolCall;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("agent_tool_calls")
public class AgentToolCallEntity {

    @Id
    private UUID id;
    private UUID runId;
    private UUID sessionId;
    private String ownerId;
    private String workspaceId;
    private String toolName;
    private boolean mcp;
    private String status;
    private String idempotencyKey;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;

    // 将工具调用实体转换为审计视图。
    public AgentToolCall toContract() {
        return new AgentToolCall(
                id,
                runId,
                sessionId,
                ownerId,
                workspaceId,
                toolName,
                mcp,
                status,
                idempotencyKey,
                startedAt == null ? null : startedAt.toInstant(),
                finishedAt == null ? null : finishedAt.toInstant());
    }
}
