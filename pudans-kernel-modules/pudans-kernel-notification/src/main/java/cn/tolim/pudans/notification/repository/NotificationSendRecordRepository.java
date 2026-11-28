package cn.tolim.pudans.notification.repository;

import cn.tolim.pudans.notification.entity.NotificationSendRecordEntity;
import cn.tolim.pudans.notification.enums.SendStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationSendRecordRepository
        extends JpaRepository<NotificationSendRecordEntity, String>,
        JpaSpecificationExecutor<NotificationSendRecordEntity> {

    Optional<NotificationSendRecordEntity> findByRequestId(String requestId);

    // Claims a record for delivery in a multi-instance deployment.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from NotificationSendRecordEntity r where r.id = :id")
    Optional<NotificationSendRecordEntity> findLockedById(@Param("id") String id);

    List<NotificationSendRecordEntity> findByStatusInAndNextRetryAtBeforeOrderByNextRetryAtAsc(
            List<SendStatus> statuses, LocalDateTime dueBefore, Pageable pageable);

    long countByStatus(SendStatus status);

    @Query("select r.status, count(r) from NotificationSendRecordEntity r group by r.status")
    List<Object[]> countGroupedByStatus();
}