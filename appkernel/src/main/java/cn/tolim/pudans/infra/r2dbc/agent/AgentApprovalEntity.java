package cn.tolim.pudans.infra.r2dbc.agent;

import cn.tolim.pudans.contract.agent.AgentApproval;
import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.OffsetDateTime;
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
@Table("agent_approvals")
public class AgentApprovalEntity {

    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() {};

    @Id
    private UUID id;
    private UUID runId;
    private UUID toolCallId;
    private String status;
    private String requestJson;
    private String decisionJson;
    private String decidedBy;
    private OffsetDateTime decidedAt;
    private OffsetDateTime expiresAt;

    // 将审批实体转换为接口视图。
    public AgentApproval toContract() {
        return new AgentApproval(
                id,
                runId,
                toolCallId,
                status,
                JsonUtils.jsonToObject(requestJson, OBJECT),
                decisionJson == null ? null : JsonUtils.jsonToObject(decisionJson, OBJECT),
                decidedBy,
                decidedAt == null ? null : decidedAt.toInstant(),
                expiresAt == null ? null : expiresAt.toInstant());
    }
}
