package cn.tolim.pudans.iam.communal.repository.account;

import cn.tolim.pudans.iam.communal.entity.account.UserHistoryPasswordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface UserHistoryPasswordRepository extends
        JpaRepository<UserHistoryPasswordEntity, String> {
}
