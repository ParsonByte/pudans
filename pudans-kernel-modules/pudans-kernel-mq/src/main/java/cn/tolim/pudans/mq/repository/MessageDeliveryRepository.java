package cn.tolim.pudans.mq.repository;

import cn.tolim.pudans.mq.entity.MessageDeliveryEntity;
import cn.tolim.pudans.mq.enums.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageDeliveryRepository extends JpaRepository<MessageDeliveryEntity, String> {

    Optional<MessageDeliveryEntity> findByMessageIdAndConsumerGroup(String messageId, String consumerGroup);

    List<MessageDeliveryEntity> findByMessageId(String messageId);

    long countByStatus(DeliveryStatus status);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update MessageDeliveryEntity d set d.status = :retrying, d.nextAttemptAt = :now, "
            + "d.version = d.version + 1 where d.status = :delivering and d.lastAttemptAt < :threshold")
    int recoverStuckDeliveries(@Param("retrying") DeliveryStatus retrying,
                               @Param("delivering") DeliveryStatus delivering,
                               @Param("now") LocalDateTime now,
                               @Param("threshold") LocalDateTime threshold);
}