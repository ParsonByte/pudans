package cn.tolim.pudans.iam.communal.repository.account.impl;

import cn.tolim.pudans.iam.communal.entity.account.po.UserIdpBindPO;
import cn.tolim.pudans.iam.communal.repository.account.UserIdpRepositoryCustomized;
import cn.tolim.pudans.iam.infra.repository.aspect.query.QuerySingleResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.AllArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static cn.tolim.pudans.iam.communal.constant.AccountConstants.USER_CACHE_NAME;


@Repository
@CacheConfig(cacheNames = {USER_CACHE_NAME})
@AllArgsConstructor
public class UserIdpRepositoryCustomizedImpl implements UserIdpRepositoryCustomized {

    // EntityManager
    private final EntityManager entityManager;

    // Query by ID
    // Parameter id: String
    // Returns: Optional
    @Override
    @QuerySingleResult
    public Optional<UserIdpBindPO> selectById(String id) {
        String hql = """
                SELECT
                    NEW cn.tolim.pudans.iam.communal.entity.account.po.UserIdpBindPO(uib.id,user.id,user.username,user.nickName,user.email,user.phone,user.phoneAreaCode,user.avatar,tpu.openId,tpu.unionId,idp.id,idp.name,idp.type,uib.bindTime)
                FROM
                    UserIdpBindEntity uib
                    LEFT JOIN UserEntity user ON uib.userId = user.id
                    LEFT JOIN ThirdPartyUserEntity tpu ON uib.thirdPartyUser.id = tpu.id
                    LEFT JOIN IdentityProviderEntity idp ON tpu.idpId = idp.id
                WHERE
                    uib.id =:id
                """;
        TypedQuery<UserIdpBindPO> query = entityManager.createQuery(hql, UserIdpBindPO.class);
        query.setParameter("id", id);
        return Optional.of(query.getSingleResult());
    }

    // Query by identity source ID and OpenID
    // Parameter idpId: String
    // Parameter openId: String
    // Returns: Optional
    @Override
    @QuerySingleResult
    public Optional<UserIdpBindPO> findByIdpIdAndOpenId(String idpId, String openId) {
        String hql = """
                SELECT
                    NEW cn.tolim.pudans.iam.communal.entity.account.po.UserIdpBindPO(uib.id,user.id,user.username,user.nickName,user.email,user.phone,user.phoneAreaCode,user.avatar,tpu.openId,tpu.unionId,idp.id,idp.name,idp.type,uib.bindTime)
                FROM
                    UserIdpBindEntity uib
                    LEFT JOIN UserEntity user ON uib.userId = user.id
                    LEFT JOIN ThirdPartyUserEntity tpu ON uib.thirdPartyUser.id = tpu.id
                    LEFT JOIN IdentityProviderEntity idp ON tpu.idpId = idp.id
                WHERE
                    tpu.idpId =:idpId
                    AND tpu.openId =:openId
                """;
        TypedQuery<UserIdpBindPO> query = entityManager.createQuery(hql, UserIdpBindPO.class);
        query.setParameter("idpId", idpId);
        query.setParameter("openId", openId);
        return Optional.of(query.getSingleResult());
    }

    // Query by identity source ID and user ID
    // Parameter idpId: String
    // Parameter userId: String
    // Returns: Optional
    @Override
    @QuerySingleResult
    public Optional<UserIdpBindPO> findByIdpIdAndUserId(String idpId, String userId) {
        String hql = """
                SELECT
                    NEW cn.tolim.pudans.iam.communal.entity.account.po.UserIdpBindPO(uib.id,user.id,user.username,user.nickName,user.email,user.phone,user.phoneAreaCode,user.avatar,tpu.openId,tpu.unionId,idp.id,idp.name,idp.type,uib.bindTime)
                FROM
                    UserIdpBindEntity uib
                    LEFT JOIN UserEntity user ON uib.userId = user.id
                    LEFT JOIN ThirdPartyUserEntity tpu ON uib.thirdPartyUser.id = tpu.id
                    LEFT JOIN IdentityProviderEntity idp ON tpu.idpId = idp.id
                WHERE
                     uib.userId =:userId
                     AND tpu.idpId =:idpId
                """;
        TypedQuery<UserIdpBindPO> query = entityManager.createQuery(hql, UserIdpBindPO.class);
        query.setParameter("userId", userId);
        query.setParameter("idpId", idpId);
        return Optional.of(query.getSingleResult());
    }

    // Returns identity provider bindings for the given user.
    @Override
    public Iterable<UserIdpBindPO> getUserIdpBindList(String userId) {
        String hql = """
                SELECT
                    NEW cn.tolim.pudans.iam.communal.entity.account.po.UserIdpBindPO(uib.id,user.id,user.username,user.nickName,user.email,user.phone,user.phoneAreaCode,user.avatar,uib.thirdPartyUser.openId,uib.thirdPartyUser.unionId,idp.id,idp.name,idp.type,uib.bindTime)
                FROM
                    UserIdpBindEntity uib
                    LEFT JOIN UserEntity user ON uib.userId = user.id
                    LEFT JOIN IdentityProviderEntity idp ON uib.thirdPartyUser.idpId = idp.id
                WHERE
                    uib.userId =:userId
                """;
        TypedQuery<UserIdpBindPO> query = entityManager.createQuery(hql, UserIdpBindPO.class);
        query.setParameter("userId", userId);
        return query.getResultList();
    }
}
