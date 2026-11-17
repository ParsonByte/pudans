package cn.tolim.pudans.infra.audit;

import cn.tolim.pudans.contract.core.extension.audit.AuditRecord;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("audit_record_archive")
public class AuditArchiveEntity {

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

    @Column("is_archived_at")
    private OffsetDateTime archivedAt;

    public static AuditArchiveEntity from(AuditRecordEntity active) {
        var archive = new AuditArchiveEntity();
        archive.setId(active.getId());
        archive.setTraceId(active.getTraceId());
        archive.setTraceFlowId(active.getTraceFlowId());
        archive.setParentAuditId(active.getParentAuditId());
        archive.setStepIndex(active.getStepIndex());
        archive.setUid(active.getUid());
        archive.setOperation(active.getOperation());
        archive.setResourceType(active.getResourceType());
        archive.setResourceName(active.getResourceName());
        archive.setRequestMethod(active.getRequestMethod());
        archive.setRequestPath(active.getRequestPath());
        archive.setIp(active.getIp());
        archive.setUserAgent(active.getUserAgent());
        archive.setOccurredAt(active.getOccurredAt());
        archive.setResult(active.getResult());
        archive.setFingerprint(active.getFingerprint());
        archive.setDurationMs(active.getDurationMs());
        archive.setContextJson(active.getContextJson());
        archive.setActionSnapshot(active.getActionSnapshot());
        archive.setArchivedAt(OffsetDateTime.now(java.time.ZoneOffset.UTC));
        return archive;
    }

    public AuditRecord toRecord() {
        var record = new AuditRecord();
        var metadata = new cn.tolim.pudans.contract.extension.Metadata();
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
        var context = AuditRecordEntity.parseContext(contextJson);
        record.setSyntheticFlow(Boolean.TRUE.equals(context.remove("syntheticFlow")));
        record.setContext(context);
        record.setActionSnapshot(actionSnapshot);
        record.setArchived(true);
        return record;
    }
}
