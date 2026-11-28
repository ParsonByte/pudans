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
public interface LoginSessionRepository extends JpaRepository<LoginSessionEntity, String> {

    // Returns the global login session with the given sid, if present
    Optional<LoginSessionEntity> findBySid(String sid);

    // Returns all active global login sessions of a user
    // Parameter userId: String
    // Parameter now: LocalDateTime
    // Returns: List<LoginSessionEntity>
    @Query("SELECT s FROM LoginSessionEntity s WHERE s.userId = :userId AND s.revoked = false "
            + "AND s.expiresAt > :now ORDER BY s.createdAt DESC")
    List<LoginSessionEntity> findActiveByUser(@Param("userId") String userId, @Param("now") LocalDateTime now);

    // Returns all sessions of a user, including revoked and expired ones
    List<LoginSessionEntity> findAllByUserIdOrderByCreatedAtDesc(String userId);

    // Returns all not revoked sessions of a user
    List<LoginSessionEntity> findAllByUserIdAndRevokedFalse(String userId);

    // Revokes a single session
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    @Query("UPDATE LoginSessionEntity s SET s.revoked = true, s.revokeReason = :reason WHERE s.sid = :sid")
    int revokeBySid(@Param("sid") String sid, @Param("reason") String reason);

    // Revokes every active session of a user, used for logout, password change or account disable
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    @Query("UPDATE LoginSessionEntity s SET s.revoked = true, s.revokeReason = :reason "
            + "WHERE s.userId = :userId AND s.revoked = false")
    int revokeAllByUserId(@Param("userId") String userId, @Param("reason") String reason);

    // Updates the last seen time of a session
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    @Query("UPDATE LoginSessionEntity s SET s.lastSeenAt = :lastSeenAt WHERE s.sid = :sid")
    int touch(@Param("sid") String sid, @Param("lastSeenAt") LocalDateTime lastSeenAt);

    // Deletes sessions that expired before the given time
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    @Query("DELETE FROM LoginSessionEntity s WHERE s.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}