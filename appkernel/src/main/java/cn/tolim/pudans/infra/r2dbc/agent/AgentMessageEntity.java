package cn.tolim.pudans.infra.r2dbc.agent;

import cn.tolim.pudans.contract.agent.AgentMessage;
import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("agent_messages")
public class AgentMessageEntity {

    private static final TypeReference<List<UUID>> ASSET_IDS = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> TOOL_CALLS = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() {};

    @Id
    private UUID id;
    private UUID sessionId;
    private UUID runId;
    private String role;
    private String contentJson;
    private String assetIdsJson;
    private String toolCallsJson;
    private String usageJson;
    private OffsetDateTime createdAt;

    // 将消息实体还原为可返回的结构化内容。
    public AgentMessage toContract() {
        return new AgentMessage(
                id,
                sessionId,
                runId,
                role,
                JsonUtils.jsonToObject(contentJson, OBJECT),
                JsonUtils.jsonToObject(assetIdsJson, ASSET_IDS),
                JsonUtils.jsonToObject(toolCallsJson, TOOL_CALLS),
                JsonUtils.jsonToObject(usageJson, OBJECT),
                createdAt == null ? null : createdAt.toInstant());
    }

    // 创建用户消息实体并保留原始结构化内容。
    public static AgentMessageEntity userMessage(
            UUID sessionId,
            UUID runId,
            Map<String, Object> content,
            OffsetDateTime now) {
        var entity = new AgentMessageEntity();
        entity.setId(UUID.randomUUID());
        entity.setSessionId(sessionId);
        entity.setRunId(runId);
        entity.setRole("user");
        entity.setContentJson(JsonUtils.objectToJson(content));
        entity.setAssetIdsJson("[]");
        entity.setToolCallsJson("[]");
        entity.setUsageJson("{}");
        entity.setCreatedAt(now);
        return entity;
    }

    // 创建助手消息并保留工具调用与模型用量。
    public static AgentMessageEntity assistantMessage(
            UUID sessionId,
            UUID runId,
            Map<String, Object> content,
            List<Map<String, Object>> toolCalls,
            Map<String, Object> usage,
            OffsetDateTime now) {
        var entity = new AgentMessageEntity();
        entity.setId(UUID.randomUUID());
        entity.setSessionId(sessionId);
        entity.setRunId(runId);
        entity.setRole("assistant");
        entity.setContentJson(JsonUtils.objectToJson(content == null ? Map.of() : content));
        entity.setAssetIdsJson("[]");
        entity.setToolCallsJson(JsonUtils.objectToJson(toolCalls == null ? List.of() : toolCalls));
        entity.setUsageJson(JsonUtils.objectToJson(usage == null ? Map.of() : usage));
        entity.setCreatedAt(now);
        return entity;
    }
}
