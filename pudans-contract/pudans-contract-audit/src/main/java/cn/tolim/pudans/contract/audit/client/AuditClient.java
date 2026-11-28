package cn.tolim.pudans.contract.audit.client;

import cn.tolim.pudans.contract.audit.AuditContractConstants;
import cn.tolim.pudans.contract.audit.AuditApiPaths;
import cn.tolim.pudans.contract.audit.dto.AuditApiResult;
import cn.tolim.pudans.contract.audit.dto.AuditEventTypeView;
import cn.tolim.pudans.contract.audit.dto.AuditPage;
import cn.tolim.pudans.contract.audit.dto.AuditQueryRequest;
import cn.tolim.pudans.contract.audit.dto.AuditRecordView;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

// Audit service contract
@FeignClient(name = AuditContractConstants.SERVICE_NAME, contextId = "auditClient",
        path = AuditApiPaths.BASE)
public interface AuditClient {

    // Search audit records
    // Parameter query: AuditQueryRequest
    // Returns: AuditApiResult<AuditPage<AuditRecordView>>
    @GetMapping(AuditApiPaths.RECORDS_SUFFIX)
    AuditApiResult<AuditPage<AuditRecordView>> search(@SpringQueryMap AuditQueryRequest query);

    // Get one audit record
    // Parameter id: String
    // Returns: AuditApiResult<AuditRecordView>
    @GetMapping(AuditApiPaths.RECORD_DETAIL_SUFFIX)
    AuditApiResult<AuditRecordView> getRecord(@PathVariable("id") String id);

    // Get the ordered audit trail of one request
    // Parameter requestId: String
    // Returns: AuditApiResult<List<AuditRecordView>>
    @GetMapping(AuditApiPaths.RECORDS_BY_REQUEST_SUFFIX)
    AuditApiResult<List<AuditRecordView>> findByRequest(@PathVariable("requestId") String requestId);

    // Get the ordered audit trail of one global login session
    // Parameter sessionId: String
    // Returns: AuditApiResult<List<AuditRecordView>>
    @GetMapping(AuditApiPaths.RECORDS_BY_SESSION_SUFFIX)
    AuditApiResult<List<AuditRecordView>> findBySession(@PathVariable("sessionId") String sessionId);

    // List audit event types
    // Returns: AuditApiResult<List<AuditEventTypeView>>
    @GetMapping(AuditApiPaths.EVENT_TYPES_SUFFIX)
    AuditApiResult<List<AuditEventTypeView>> listEventTypes();
}