package cn.tolim.pudans.audit.endpoint;

import cn.tolim.pudans.audit.constant.AuditApiPaths;
import cn.tolim.pudans.audit.dto.AuditEventTypeView;
import cn.tolim.pudans.audit.dto.AuditPage;
import cn.tolim.pudans.audit.dto.AuditRecordView;
import cn.tolim.pudans.audit.event.type.EventType;
import cn.tolim.pudans.audit.query.AuditQuery;
import cn.tolim.pudans.audit.service.AuditQueryService;
import cn.tolim.pudans.iam.infra.result.ApiRestResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Tag(name = "IAM 审计记录")
@RestController
@RequestMapping(AuditApiPaths.AUDIT)
public class AuditRecordEndpoint {

    private final AuditQueryService auditQueryService;

    public AuditRecordEndpoint(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    // Search audit records with paging and filters
    @Operation(summary = "分页查询审计记录")
    @GetMapping(AuditApiPaths.RECORDS)
    public ApiRestResult<AuditPage<AuditRecordView>> search(@ModelAttribute AuditQuery query) {
        try {
            return ApiRestResult.ok(auditQueryService.search(query));
        } catch (IllegalArgumentException e) {
            return invalidArgument(e);
        }
    }

    @Operation(summary = "查询审计记录详情")
    @GetMapping(AuditApiPaths.RECORD)
    public ApiRestResult<AuditRecordView> get(@PathVariable String id) {
        Optional<AuditRecordView> record = auditQueryService.findById(id);
        if (record.isPresent()) {
            return ApiRestResult.ok(record.get());
        }
        return ApiRestResult.err("Audit record not found: " + id,
                String.valueOf(HttpStatus.NOT_FOUND.value()));
    }

    // Ordered audit trail of one request
    @Operation(summary = "按请求 ID 查询审计记录")
    @GetMapping(AuditApiPaths.RECORDS_BY_REQUEST)
    public ApiRestResult<List<AuditRecordView>> findByRequest(@PathVariable String requestId) {
        return ApiRestResult.ok(auditQueryService.findByRequestId(requestId));
    }

    // Ordered audit trail of one global login session
    @Operation(summary = "按会话 ID 查询审计记录")
    @GetMapping(AuditApiPaths.RECORDS_BY_SESSION)
    public ApiRestResult<List<AuditRecordView>> findBySession(@PathVariable String sessionId) {
        return ApiRestResult.ok(auditQueryService.findBySessionId(sessionId));
    }

    // Metadata of all audit event types, used to build query filters
    @Operation(summary = "查询审计事件类型")
    @GetMapping(AuditApiPaths.EVENT_TYPES)
    public ApiRestResult<List<AuditEventTypeView>> eventTypes() {
        return ApiRestResult.ok(Arrays.stream(EventType.values()).map(AuditEventTypeView::from)
                .toList());
    }

    private static <T> ApiRestResult<T> invalidArgument(IllegalArgumentException e) {
        return ApiRestResult.err(e.getMessage(), String.valueOf(HttpStatus.BAD_REQUEST.value()));
    }
}