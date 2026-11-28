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
public interface AppSessionRepository extends JpaRepository<AppSessionEntity, String> {

    // Returns the application session cache bound to the given application code and sid
    Optional<AppSessionEntity> findByAppCodeAndSid(String appCode, String sid);

    // Returns the active application session cache bound to the given application code and sid
    // Parameter appCode: String
    // Parameter sid: String
    // Parameter now: LocalDateTime
    // Returns: Optional<AppSessionEntity>
    @Query("SELECT a FROM AppSessionEntity a WHERE a.appCode = :appCode AND a.sid = :sid "
            + "AND a.revoked = false AND a.expiresAt > :now")
    Optional<AppSessionEntity> findActiveByAppCodeAndSid(@Param("appCode") String appCode,
                                                         @Param("sid") String sid,
                                                         @Param("now") LocalDateTime now);

    // Returns all application session caches bound to a global login session
    List<AppSessionEntity> findAllBySid(String sid);

    // Returns all application session caches of a user
    List<AppSessionEntity> findAllByUserIdOrderByCreatedAtDesc(String userId);

    // Returns all application session caches of a user for an application
    List<AppSessionEntity> findAllByAppCodeAndUserId(String appCode, String userId);

    // Updates the last seen time of an application session cache
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    @Query("UPDATE AppSessionEntity a SET a.lastSeenAt = :lastSeenAt WHERE a.appCode = :appCode AND a.sid = :sid")
    int touch(@Param("appCode") String appCode,
              @Param("sid") String sid,
              @Param("lastSeenAt") LocalDateTime lastSeenAt);

    // Revokes every application session cache bound to a global login session
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    @Query("UPDATE AppSessionEntity a SET a.revoked = true WHERE a.sid = :sid AND a.revoked = false")
    int revokeAllBySid(@Param("sid") String sid);

    // Revokes the application session cache of one application
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    @Query("UPDATE AppSessionEntity a SET a.revoked = true WHERE a.appCode = :appCode AND a.sid = :sid")
    int revokeByAppCodeAndSid(@Param("appCode") String appCode, @Param("sid") String sid);

    // Records that a back-channel logout notification was sent for an application session cache
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    @Query("UPDATE AppSessionEntity a SET a.lastBackchannelAt = :notifiedAt, a.revoked = true "
            + "WHERE a.appCode = :appCode AND a.sid = :sid")
    int markBackchannelLogout(@Param("appCode") String appCode,
                              @Param("sid") String sid,
                              @Param("notifiedAt") LocalDateTime notifiedAt);

    // Deletes application session caches that expired before the given time
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    @Query("DELETE FROM AppSessionEntity a WHERE a.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}