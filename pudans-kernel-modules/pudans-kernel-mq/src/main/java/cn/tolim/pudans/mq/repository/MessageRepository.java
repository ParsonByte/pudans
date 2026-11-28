package cn.tolim.pudans.mq.repository;

import cn.tolim.pudans.mq.entity.MessageEntity;
import cn.tolim.pudans.mq.enums.MessageStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<MessageEntity, String>,
        JpaSpecificationExecutor<MessageEntity> {

    Optional<MessageEntity> findByBizKey(String bizKey);

    boolean existsByBizKey(String bizKey);

    @Query("select m.id from MessageEntity m "
            + "where (m.status = :pending or m.status = :retrying) "
            + "and (m.nextAttemptAt is null or m.nextAttemptAt <= :now) "
            + "order by m.createTime asc")
    List<String> findDispatchableIds(@Param("pending") MessageStatus pending,
                                     @Param("retrying") MessageStatus retrying,
                                     @Param("now") LocalDateTime now,
                                     Pageable pageable);

    // Conditional claim keeps competing dispatcher instances from delivering the same round
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update MessageEntity m set m.status = :target, m.attempts = m.attempts + 1, "
            + "m.lastAttemptAt = :now, m.nextAttemptAt = null, m.version = m.version + 1 "
            + "where m.id = :id and (m.status = :pending or m.status = :retrying)")
    int claim(@Param("id") String id,
              @Param("target") MessageStatus target,
              @Param("pending") MessageStatus pending,
              @Param("retrying") MessageStatus retrying,
              @Param("now") LocalDateTime now);

    // Returns messages stuck in DISPATCHING after a dispatcher crash
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update MessageEntity m set m.status = :pending, m.nextAttemptAt = :now, "
            + "m.version = m.version + 1 where m.status = :dispatching and m.lastAttemptAt < :threshold")
    int recoverStuck(@Param("pending") MessageStatus pending,
                     @Param("dispatching") MessageStatus dispatching,
                     @Param("now") LocalDateTime now,
                     @Param("threshold") LocalDateTime threshold);

    long countByStatus(MessageStatus status);
}