package cn.tolim.pudans.iam.communal.repository.account;

import cn.tolim.pudans.iam.communal.entity.account.ThirdPartyUserEntity;
import cn.tolim.pudans.iam.communal.entity.account.UserIdpBindEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;


@Repository
public interface UserIdpRepository extends JpaRepository<UserIdpBindEntity, String>,
        UserIdpRepositoryCustomized {

    long countByThirdPartyUser(ThirdPartyUserEntity thirdPartyUserEntity);

    // Delete by user ID
    // Parameter userIds: String
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    void deleteAllByUserIdIn(Collection<String> userIds);
}
