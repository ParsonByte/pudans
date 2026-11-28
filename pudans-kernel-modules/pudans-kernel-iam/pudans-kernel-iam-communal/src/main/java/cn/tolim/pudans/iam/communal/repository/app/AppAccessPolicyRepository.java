package cn.tolim.pudans.iam.communal.repository.app;

import cn.tolim.pudans.iam.communal.entity.app.AppAccessPolicyEntity;
import cn.tolim.pudans.iam.communal.enums.app.AppPolicySubjectType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;


@Repository
public interface AppAccessPolicyRepository extends JpaRepository<AppAccessPolicyEntity, String>,
        AppAccessPolicyRepositoryCustomized {
    // Delete all data by application ID
    // Parameter appId: String
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    void deleteAllByAppId(@Param("appId") String appId);

    // Delete all data by principal ID
    // Parameter subjectId: String
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    void deleteAllBySubjectId(@Param("subjectId") String subjectId);

    // Delete all data by principal ID and application list
    // Parameter subjectId: String
    // Parameter appIds: List <String>
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    void deleteAllBySubjectIdAndAppIdIn(@Param("subjectId") String subjectId,
                                        @Param("appIds") List<String> appIds);

    // Query by application ID, principal ID, and principal type
    // Parameter appId: String
    // Parameter subjectId: String
    // Parameter subjectType: AppPolicySubjectType
    // Returns: AppAccessPolicyEntity
    Optional<AppAccessPolicyEntity> findByAppIdAndSubjectIdAndSubjectType(String appId,
                                                                          String subjectId,
                                                                          AppPolicySubjectType subjectType);

    // Update application access authorization status
    // Parameter id: String
    // Parameter enabled: Boolean
    // Returns: Integer
    @Transactional(rollbackFor = Exception.class)
    @Modifying
    @Query(value = "UPDATE AppAccessPolicyEntity SET enabled = :enabled WHERE id = :id")
    Integer updateStatus(@Param(value = "id") String id, @Param(value = "enabled") Boolean enabled);
}
