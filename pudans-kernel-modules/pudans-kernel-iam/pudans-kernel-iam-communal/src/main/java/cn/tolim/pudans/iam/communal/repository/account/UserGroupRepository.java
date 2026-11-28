package cn.tolim.pudans.iam.communal.repository.account;

import cn.tolim.pudans.iam.communal.entity.account.UserGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;


@Repository
public interface UserGroupRepository extends JpaRepository<UserGroupEntity, String>,
        JpaSpecificationExecutor<UserGroupEntity> {

}
