package cn.tolim.pudans.iam.communal.repository.account;

import cn.tolim.pudans.iam.communal.entity.account.po.UserIdpBindPO;

import java.util.Optional;


public interface UserIdpRepositoryCustomized {

    Optional<UserIdpBindPO> selectById(String id);

    // Query by identity source ID and OpenID
    // Parameter idpId: String
    // Parameter openId: String
    // Returns: Optional
    Optional<UserIdpBindPO> findByIdpIdAndOpenId(String idpId, String openId);

    // Query by identity source ID and user ID
    // Parameter idpId: String
    // Parameter userId: String
    // Returns: Optional
    Optional<UserIdpBindPO> findByIdpIdAndUserId(String idpId, String userId);

    // Returns identity provider bindings for the given user.
    Iterable<UserIdpBindPO> getUserIdpBindList(String userId);
}
