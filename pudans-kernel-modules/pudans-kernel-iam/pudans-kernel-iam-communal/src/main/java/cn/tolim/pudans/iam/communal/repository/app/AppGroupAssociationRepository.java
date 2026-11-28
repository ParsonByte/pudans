package cn.tolim.pudans.iam.communal.repository.app;

import cn.tolim.pudans.iam.communal.entity.app.AppEntity;
import cn.tolim.pudans.iam.communal.entity.app.AppGroupAssociationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Repository
public interface AppGroupAssociationRepository extends
        JpaRepository<AppGroupAssociationEntity, String>,
        AppGroupAssociationRepositoryCustomized {

    // Delete by application group ID and application ID
    // Parameter groupId: String
    // Parameter appId: String
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    @Query("DELETE FROM AppGroupAssociationEntity ass WHERE ass.groupId = :groupId and ass.app.id = :appId ")
    void deleteByGroupIdAndAppId(@Param("groupId") String groupId, @Param("appId") String appId);

    // Delete associations by application ID
    // Parameter appEntity: AppEntity
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    void deleteByApp(AppEntity appEntity);

    // Delete associations by application group ID
    // Parameter groupId: String
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    void deleteAllByGroupId(@Param(value = "groupId") String groupId);

    // Query association information by application ID
    // Parameter appId: String
    // Returns: List
    @Query("SELECT ass.groupId FROM AppGroupAssociationEntity ass WHERE ass.app.id = :appId")
    List<String> findGroupIdByAppId(String appId);

    // By application ID
    // Parameter appEntity: AppEntity
    // Returns: List
    List<AppGroupAssociationEntity> findByApp(AppEntity appEntity);
}
