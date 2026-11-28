package cn.tolim.pudans.contract.audit.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

// Audit record returned by the query API
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuditRecordView implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Audit record ID
    private String id;

    // Request ID
    private String requestId;

    // Global login session ID
    private String sessionId;

    // Actor ID
    private String actorId;

    // Actor type: user or admin
    private String actorType;

    // Authentication type used by the actor
    private String actorAuthType;

    // Event type code
    private String eventType;

    // Event type name
    private String eventTypeName;

    // Audit category code
    private String eventCategory;

    // Event status code: success or fail
    private String eventStatus;

    // Event status name
    private String eventStatusName;

    // Event content
    private String eventContent;

    // Event parameters
    private String eventParam;

    // Event result
    private String eventResult;

    // Event time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime eventTime;

    // Creation time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    // Targets
    private List<AuditTarget> targets;

    // User agent
    private AuditUserAgent userAgent;

    // Geographic location
    private AuditGeoLocation geoLocation;
}
