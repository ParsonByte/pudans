package cn.tolim.pudans.infra.r2dbc.agent;

import cn.tolim.pudans.contract.agent.AgentRun;
import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("agent_runs")
public class AgentRunEntity {

    private static final TypeReference<List<String>> TOOL_IDS = new TypeReference<>() {};

    @Id
    private UUID id;
    private UUID sessionId;
    private UUID inputMessageId;
    private UUID outputMessageId;
    private String status;
    private UUID modelId;
    private String answerMode;
    private String reasoning;
    private String toolIdsJson;
    private UUID quotaReservationId;
    private boolean providerRequestSent;
    private boolean cancelRequested;
    private String errorCode;
    private String errorMessage;
    private OffsetDateTime createdAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;

    // 将运行实体转换为接口状态视图。
    public AgentRun toContract() {
        return new AgentRun(
                id,
                sessionId,
                inputMessageId,
                outputMessageId,
                status,
                modelId,
                answerMode,
                reasoning,
                JsonUtils.jsonToObject(toolIdsJson, TOOL_IDS),
                quotaReservationId,
                providerRequestSent,
                cancelRequested,
                errorCode,
                errorMessage,
                createdAt == null ? null : createdAt.toInstant(),
                startedAt == null ? null : startedAt.toInstant(),
                finishedAt == null ? null : finishedAt.toInstant());
    }

    // 创建排队中的运行实体，输入消息由调用方先行落库。
    public static AgentRunEntity queued(
            UUID sessionId,
            UUID inputMessageId,
            UUID modelId,
            String answerMode,
            String reasoning,
            List<String> toolIds,
            OffsetDateTime now) {
        var entity = new AgentRunEntity();
        entity.setId(UUID.randomUUID());
        entity.setSessionId(sessionId);
        entity.setInputMessageId(inputMessageId);
        entity.setStatus("queued");
        entity.setModelId(modelId);
        entity.setAnswerMode(answerMode);
        entity.setReasoning(reasoning);
        entity.setToolIdsJson(JsonUtils.objectToJson(toolIds == null ? List.of() : toolIds));
        entity.setCreatedAt(now);
        return entity;
    }
}
