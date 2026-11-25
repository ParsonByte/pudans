package cn.tolim.pudans.infra.r2dbc.agent;

import cn.tolim.pudans.contract.agent.AgentSession;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("agent_sessions")
public class AgentSessionEntity {

    @Id
    private UUID id;
    private String ownerId;
    private String workspaceId;
    private String title;
    private String profile;
    private UUID defaultModelId;
    private String status;
    private OffsetDateTime lastMessageAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    // 将仓储实体转换为对外会话视图。
    public AgentSession toContract() {
        return new AgentSession(
                id,
                ownerId,
                workspaceId,
                title,
                profile,
                defaultModelId,
                status,
                instant(lastMessageAt),
                instant(createdAt),
                instant(updatedAt));
    }

    private static java.time.Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    // 创建新会话实体并统一初始状态。
    public static AgentSessionEntity create(
            String ownerId,
            String workspaceId,
            String title,
            String profile,
            UUID defaultModelId,
            OffsetDateTime now) {
        var entity = new AgentSessionEntity();
        entity.setId(UUID.randomUUID());
        entity.setOwnerId(ownerId);
        entity.setWorkspaceId(workspaceId);
        entity.setTitle(title);
        entity.setProfile(profile);
        entity.setDefaultModelId(defaultModelId);
        entity.setStatus("active");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    public static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
