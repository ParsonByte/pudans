package cn.tolim.pudans.mq.repository;

import cn.tolim.pudans.mq.entity.DeadLetterEntity;
import cn.tolim.pudans.mq.enums.DeadLetterStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeadLetterRepository extends JpaRepository<DeadLetterEntity, String>,
        JpaSpecificationExecutor<DeadLetterEntity> {

    List<DeadLetterEntity> findByMessageId(String messageId);

    List<DeadLetterEntity> findByMessageIdAndStatus(String messageId, DeadLetterStatus status);

    long countByStatus(DeadLetterStatus status);
}