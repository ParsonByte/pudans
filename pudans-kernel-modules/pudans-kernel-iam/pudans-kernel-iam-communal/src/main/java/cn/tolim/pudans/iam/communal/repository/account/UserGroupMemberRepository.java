package cn.tolim.pudans.iam.communal.repository.account;

import cn.tolim.pudans.iam.communal.entity.account.UserGroupMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;


@Repository
public interface UserGroupMemberRepository extends JpaRepository<UserGroupMemberEntity, String>,
        UserGroupMemberRepositoryCustomized {

    // Delete by user group ID and user ID
    // Parameter groupId: String
    // Parameter userId: String
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    void deleteByGroupIdAndUserId(@Param("groupId") String groupId, @Param("userId") String userId);

    // All user group associations for a user ID
    // Parameter userId: String
    // Returns: List
    List<UserGroupMemberEntity> findByUserId(@Param("userId") String userId);

    // All user group associations for a user group ID
    // Parameter groupId: String
    // Returns: List
    List<UserGroupMemberEntity> findByGroupId(String groupId);

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

    // Delete associations by user group ID
    // Parameter groupId: String
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    void deleteByGroupId(@Param("groupId") String groupId);
}
