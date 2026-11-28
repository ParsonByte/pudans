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

// Audit record query request
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuditQueryRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Actor ID filter
    private String actorId;

    // Actor type filter, for example user or admin
    private String actorType;

    // Authentication type filter
    private String actorAuthType;

    // Event type code filter
    private String eventType;

    // Event type codes, combined with OR
    private List<String> eventTypes;

    // Audit category code filter, for example login, session, ticket or authz
    private String category;

    // Event status filter: success or fail
    private String eventStatus;

    // Request ID filter
    private String requestId;

    // Global login session ID filter
    private String sessionId;

    // Keyword matched against content, parameters and result
    private String keyword;

    // Start of the event time range
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    // End of the event time range
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    // One-based page number, defaults to one
    @Builder.Default
    private Integer page = 1;

    // Page size, defaults to 20
    @Builder.Default
    private Integer size = 20;

    // Sort field: eventTime, createTime, actorId or eventType
    @Builder.Default
    private String sortBy = "eventTime";

    // Sort direction: asc or desc
    @Builder.Default
    private String sortDirection = "desc";
}
