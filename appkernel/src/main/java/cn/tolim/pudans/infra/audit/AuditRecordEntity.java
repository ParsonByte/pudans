package cn.tolim.pudans.infra.audit;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import cn.tolim.pudans.contract.audit.AuditRecord;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.infra.contract.utils.JsonUtils;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("audit_record")
public class AuditRecordEntity {

    @Id
    private String id;
    private String traceId;
    private String traceFlowId;
    private String parentAuditId;
    private Integer stepIndex;
    private String uid;
    private String operation;
    private String resourceType;
    private String resourceName;
    private String requestMethod;
    private String requestPath;
    private String ip;
    private String userAgent;
    private OffsetDateTime occurredAt;
    private String result;
    private String fingerprint;
    private Long durationMs;
    private String contextJson;
    private String actionSnapshot;

    // 将审计记录转换为实体，时间统一存为协调世界时。
    public static AuditRecordEntity from(AuditRecord record) {
        var entity = new AuditRecordEntity();
        entity.setId(record.getMetadata().getName());
        entity.setTraceId(record.getTraceId());
        entity.setTraceFlowId(record.getTraceFlowId());
        entity.setParentAuditId(record.getParentAuditId());
        entity.setStepIndex(record.getStepIndex());
        entity.setUid(record.getUid());
        entity.setOperation(record.getOperation());
        entity.setResourceType(record.getResourceType());
        entity.setResourceName(record.getResourceName());
        entity.setRequestMethod(record.getRequestMethod());
        entity.setRequestPath(record.getRequestPath());
        entity.setIp(record.getIp());
        entity.setUserAgent(record.getUserAgent());
        entity.setOccurredAt(record.getTimestamp() == null
                ? OffsetDateTime.now(java.time.ZoneOffset.UTC)
                : OffsetDateTime.ofInstant(record.getTimestamp(), java.time.ZoneOffset.UTC));
        entity.setResult(record.getResult());
        entity.setFingerprint(record.getFingerprint());
        entity.setDurationMs(record.getDurationMs());
        var context = record.getContext() == null
                ? new java.util.LinkedHashMap<String, Object>()
                : new java.util.LinkedHashMap<>(record.getContext());
        if (record.isSyntheticFlow()) {
            context.put("syntheticFlow", Boolean.TRUE);
        }
        entity.setContextJson(JsonUtils.objectToJson(context));
        entity.setActionSnapshot(record.getActionSnapshot());
        return entity;
    }

    // 还原审计记录，并恢复合成流程标记。
    public AuditRecord toRecord(boolean archived) {
        var record = new AuditRecord();
        var metadata = new Metadata();
        metadata.setName(id);
        record.setMetadata(metadata);
        record.setTraceId(traceId);
        record.setTraceFlowId(traceFlowId);
        record.setParentAuditId(parentAuditId);
        record.setStepIndex(stepIndex == null ? 1 : stepIndex);
        record.setUid(uid);
        record.setOperation(operation);
        record.setResourceType(resourceType);
        record.setResourceName(resourceName);
        record.setRequestMethod(requestMethod);
        record.setRequestPath(requestPath);
        record.setIp(ip);
        record.setUserAgent(userAgent);
        record.setTimestamp(occurredAt == null ? null : occurredAt.toInstant());
        record.setResult(result);
        record.setFingerprint(fingerprint);
        record.setDurationMs(durationMs);
        var context = parseContext(contextJson);
        record.setSyntheticFlow(Boolean.TRUE.equals(context.remove("syntheticFlow")));
        record.setContext(context);
        record.setActionSnapshot(actionSnapshot);
        record.setArchived(archived);
        return record;
    }

    // 解析上下文文本，格式异常时保留原始内容。
    @SuppressWarnings("unchecked")
    static java.util.Map<String, Object> parseContext(String json) {
        if (json == null || json.isBlank()) {
            return new java.util.LinkedHashMap<>();
        }
        try {
            return JsonUtils.jsonToObject(json, java.util.Map.class);
        } catch (Exception e) {
            var map = new java.util.LinkedHashMap<String, Object>();
            map.put("raw", json);
            return map;
        }
    }
}
