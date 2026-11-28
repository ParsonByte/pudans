package cn.tolim.pudans.iam.communal.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


@Repository
public interface SsoTicketRepository extends JpaRepository<SsoTicketEntity, String> {

    // Returns the ticket with the given value, if present
    Optional<SsoTicketEntity> findByTicket(String ticket);

    // Returns the ticket with the given value only when it was not used yet
    Optional<SsoTicketEntity> findByTicketAndUsedAtIsNull(String ticket);

    // Returns all tickets issued to an application for a global login session
    List<SsoTicketEntity> findAllBySidAndAppCode(String sid, String appCode);

    // Returns all tickets issued to a user
    List<SsoTicketEntity> findAllByUserIdOrderByCreatedAtDesc(String userId);

    // Marks a ticket as used, only succeeds once so replay is rejected
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    @Query("UPDATE SsoTicketEntity t SET t.usedAt = :usedAt WHERE t.ticket = :ticket AND t.usedAt IS NULL")
    int markUsed(@Param("ticket") String ticket, @Param("usedAt") LocalDateTime usedAt);

    // Deletes tickets that expired before the given time
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    @Query("DELETE FROM SsoTicketEntity t WHERE t.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}