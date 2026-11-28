package cn.tolim.pudans.iam.communal.repository.account;

import cn.tolim.pudans.iam.communal.entity.account.OrganizationMemberEntity;
import cn.tolim.pudans.iam.communal.entity.account.po.OrganizationMemberPO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;


@Repository
public interface OrganizationMemberRepository extends
        JpaRepository<OrganizationMemberEntity, String>,
        OrganizationMemberCustomizedRepository {

    // Delete by organization ID and user ID
    // Parameter orgId: String
    // Parameter userId: String
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    void deleteByOrgIdAndUserId(String orgId, String userId);

    // Query associated users by organization ID
    // Parameter orgId: String
    // Returns: List
    List<OrganizationMemberEntity> findAllByOrgId(String orgId);

    // Query associated users by user ID
    // Parameter userId: String
    // Returns: List
    List<OrganizationMemberEntity> findAllByUserId(String userId);

    // Batch delete associations by user ID
    // Parameter userIds: String
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    void deleteAllByUserIdIn(Collection<String> userIds);

    // Delete associations by user ID
    // Parameter id: String
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    void deleteByUserId(@Param("id") String id);

    // Query associated organizations by user ID
    // Parameter userId: String
    // Returns: List
    @Query(value = "SELECT NEW cn.tolim.pudans.iam.communal.entity.account.po.OrganizationMemberPO(om.id,om.userId,om.orgId,o.name,o.displayPath) FROM OrganizationMemberEntity om LEFT JOIN OrganizationEntity o ON o.id= om.orgId WHERE om.userId =:userId")
    List<OrganizationMemberPO> findAllPoByUserId(String userId);
}
