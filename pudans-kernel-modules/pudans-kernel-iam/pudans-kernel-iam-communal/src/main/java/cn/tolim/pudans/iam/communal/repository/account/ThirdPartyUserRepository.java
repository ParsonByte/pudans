package cn.tolim.pudans.iam.communal.repository.account;

import cn.tolim.pudans.iam.communal.entity.account.ThirdPartyUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
public interface ThirdPartyUserRepository extends JpaRepository<ThirdPartyUserEntity, String> {

    Optional<ThirdPartyUserEntity> findByOpenIdAndIdpId(String openId, String idpId);
}
