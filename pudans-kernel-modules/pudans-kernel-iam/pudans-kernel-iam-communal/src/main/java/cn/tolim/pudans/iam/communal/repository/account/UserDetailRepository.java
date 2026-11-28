package cn.tolim.pudans.iam.communal.repository.account;

import cn.tolim.pudans.iam.communal.entity.account.UserDetailEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;


@Repository
public interface UserDetailRepository extends JpaRepository<UserDetailEntity, String>,
        UserDetailRepositoryCustomized {
    // Query user details by user ID
    // Parameter user: String
    // Returns: UserDetailEntity
    Optional<UserDetailEntity> findByUserId(String user);

    // Delete user by user ID
    // Parameter userId: String
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    void deleteByUserId(@Param("userId") String userId);

    // Batch delete users by user ID
    // Parameter userIds: List
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    void deleteAllByUserIdIn(Collection<String> userIds);

    // Query user details by user ID
    // Parameter userIds: List
    // Returns: List
    List<UserDetailEntity> findAllByUserIdIn(List<String> userIds);
}
