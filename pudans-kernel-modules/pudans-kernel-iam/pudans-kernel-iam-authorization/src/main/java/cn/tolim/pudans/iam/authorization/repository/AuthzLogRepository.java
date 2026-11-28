package cn.tolim.pudans.iam.authorization.repository;

import cn.tolim.pudans.iam.authorization.entity.AuthzLogEntity;
import cn.tolim.pudans.iam.authorization.enums.PolicyDecision;
import cn.tolim.pudans.iam.authorization.enums.PrincipalType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;


@Repository
public interface AuthzLogRepository extends JpaRepository<AuthzLogEntity, String>,
        JpaSpecificationExecutor<AuthzLogEntity> {

    // Returns authorization decisions of a principal, newest first
    List<AuthzLogEntity> findAllByPrincipalTypeAndPrincipalIdOrderByCreatedAtDesc(PrincipalType principalType,
                                                                                  String principalId);

    // Returns authorization decisions for an application, newest first
    List<AuthzLogEntity> findAllByAppCodeOrderByCreatedAtDesc(String appCode);

    // Returns authorization decisions with the given result, newest first
    List<AuthzLogEntity> findAllByDecisionOrderByCreatedAtDesc(PolicyDecision decision);

    // Returns authorization decisions for an action, newest first
    List<AuthzLogEntity> findAllByActionOrderByCreatedAtDesc(String action);

    // Deletes authorization decisions older than the given time
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    @Query("DELETE FROM AuthzLogEntity l WHERE l.createdAt < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);
}