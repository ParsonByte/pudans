package cn.tolim.pudans.audit.repository;

import cn.tolim.pudans.audit.entity.AuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditRepository
        extends JpaRepository<AuditEntity, String>, JpaSpecificationExecutor<AuditEntity> {

    // All audit records produced by one request, ordered by event time
    List<AuditEntity> findAllByRequestIdOrderByEventTimeAsc(String requestId);

    // All audit records related to one login session, ordered by event time
    List<AuditEntity> findAllBySessionIdOrderByEventTimeAsc(String sessionId);
}