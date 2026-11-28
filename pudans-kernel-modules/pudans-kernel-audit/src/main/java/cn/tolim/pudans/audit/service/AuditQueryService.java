package cn.tolim.pudans.audit.service;

import cn.tolim.pudans.audit.dto.AuditPage;
import cn.tolim.pudans.audit.dto.AuditRecordView;
import cn.tolim.pudans.audit.entity.AuditEntity;
import cn.tolim.pudans.audit.query.AuditQuery;
import cn.tolim.pudans.audit.query.AuditSpecifications;
import cn.tolim.pudans.audit.repository.AuditRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class AuditQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final int MAX_PAGE_SIZE = 200;

    private static final Set<String> SORTABLE_FIELDS = Set.of("eventTime", "createTime", "actorId",
            "eventType");

    private final AuditRepository auditRepository;

    public AuditQueryService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    // Search audit records with filters and one-based pagination
    public AuditPage<AuditRecordView> search(AuditQuery query) {
        AuditQuery effectiveQuery = query == null ? new AuditQuery() : query;
        Pageable pageable = buildPageable(effectiveQuery);
        Page<AuditEntity> page = auditRepository.findAll(AuditSpecifications.of(effectiveQuery),
                pageable);
        return AuditPage.of(page.map(AuditRecordView::from));
    }

    public Optional<AuditRecordView> findById(String id) {
        if (!StringUtils.hasText(id)) {
            return Optional.empty();
        }
        return auditRepository.findById(id).map(AuditRecordView::from);
    }

    // Get the ordered audit trail of one request
    public List<AuditRecordView> findByRequestId(String requestId) {
        if (!StringUtils.hasText(requestId)) {
            return List.of();
        }
        return auditRepository.findAllByRequestIdOrderByEventTimeAsc(requestId).stream()
                .map(AuditRecordView::from).toList();
    }

    // Get the ordered audit trail of one global login session
    public List<AuditRecordView> findBySessionId(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return List.of();
        }
        return auditRepository.findAllBySessionIdOrderByEventTimeAsc(sessionId).stream()
                .map(AuditRecordView::from).toList();
    }

    private static Pageable buildPageable(AuditQuery query) {
        int pageNumber = query.getPage() == null ? 1 : Math.max(query.getPage(), 1);
        int pageSize = query.getSize() == null ? DEFAULT_PAGE_SIZE
                : Math.min(Math.max(query.getSize(), 1), MAX_PAGE_SIZE);
        Sort.Direction direction = "asc".equalsIgnoreCase(query.getSortDirection())
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortBy = StringUtils.hasText(query.getSortBy())
                && SORTABLE_FIELDS.contains(query.getSortBy()) ? query.getSortBy() : "eventTime";
        return PageRequest.of(pageNumber - 1, pageSize, Sort.by(direction, sortBy));
    }
}