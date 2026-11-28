package cn.tolim.pudans.iam.authentication.totp.repository;

import cn.tolim.pudans.iam.authentication.totp.entity.TotpCredentialEntity;
import cn.tolim.pudans.iam.authentication.totp.enums.TotpStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

// Persistence of TOTP credentials
@Repository
public interface TotpCredentialRepository extends JpaRepository<TotpCredentialEntity, String> {

    // Finds all credentials of one user
    List<TotpCredentialEntity> findAllByUserIdOrderByCreateTimeDesc(String userId);

    // Finds the newest credential of one user in the given status
    Optional<TotpCredentialEntity> findFirstByUserIdAndStatusOrderByCreateTimeDesc(String userId,
                                                                                   TotpStatus status);

    // Finds a credential by its owner and ID
    Optional<TotpCredentialEntity> findByIdAndUserId(String id, String userId);

    // Counts credentials of one user in the given status
    long countByUserIdAndStatus(String userId, TotpStatus status);

    // Deletes all credentials of one user
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    void deleteAllByUserId(String userId);

    // Deletes all credentials of one user in the given status
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    void deleteAllByUserIdAndStatus(String userId, TotpStatus status);

    // Deactivates all credentials of one user
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    @Query("update TotpCredentialEntity c set c.status = :status, c.lockedUntil = null,"
            + " c.failedAttempts = 0 where c.userId = :userId and c.status <> :status")
    int updateStatusByUserId(@Param("userId") String userId, @Param("status") TotpStatus status);
}