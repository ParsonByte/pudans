package cn.tolim.pudans.notification.repository;

import cn.tolim.pudans.notification.entity.NotificationDeadLetterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationDeadLetterRepository
        extends JpaRepository<NotificationDeadLetterEntity, String>,
        JpaSpecificationExecutor<NotificationDeadLetterEntity> {

    Optional<NotificationDeadLetterEntity> findFirstByRecordIdAndReplayedFalseOrderByCreateTimeDesc(
            String recordId);

    long countByReplayedFalse();
}