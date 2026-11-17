package cn.tolim.pudans.contract.core.extension.audit;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import cn.tolim.pudans.contract.extension.AbstractExtension;
import cn.tolim.pudans.contract.extension.GVK;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@GVK(
        group = AuditRecord.GROUP,
        version = AuditRecord.VERSION,
        kind = AuditRecord.KIND,
        plural = "auditrecords",
        singular = "auditrecord")
public class AuditRecord extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "AuditRecord";

    
    @Schema(readOnly = true)
    private String uid;

    
    @Schema(readOnly = true)
    private String traceId;

    
    @Schema(readOnly = true)
    private String traceFlowId;

    @Schema(readOnly = true)
    private boolean syntheticFlow;

    
    @Schema(readOnly = true)
    private String parentAuditId;

    
    @Schema(readOnly = true)
    private Integer stepIndex;

    
    @Schema(readOnly = true)
    private String operation;

    
    @Schema(readOnly = true)
    private String resourceType;

    
    @Schema(readOnly = true)
    private String resourceName;

    
    @Schema(readOnly = true)
    private String requestMethod;

    
    @Schema(readOnly = true)
    private String requestPath;

    
    @Schema(readOnly = true)
    private String ip;

    
    @Schema(readOnly = true)
    private String userAgent;

    
    @Schema(readOnly = true)
    private Instant timestamp;

    
    @Schema(readOnly = true)
    private String result;

    @Schema(readOnly = true)
    private String fingerprint;


    @Schema(readOnly = true)
    private Long durationMs;

    
    @Schema(readOnly = true)
    private Map<String, Object> context = new LinkedHashMap<>();

    
    @Schema(readOnly = true)
    private String actionSnapshot;

    
    @Schema(readOnly = true)
    private boolean archived;

    public void markSyntheticFlow() {
        this.syntheticFlow = true;
    }
}
