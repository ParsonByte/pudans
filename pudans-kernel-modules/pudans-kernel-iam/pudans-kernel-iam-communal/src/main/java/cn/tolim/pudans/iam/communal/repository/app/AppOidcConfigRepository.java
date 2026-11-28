package cn.tolim.pudans.iam.communal.repository.app;

import cn.tolim.pudans.iam.communal.entity.app.AppOidcConfigEntity;
import cn.tolim.pudans.iam.communal.entity.app.po.AppOidcConfigPO;
import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static cn.tolim.pudans.iam.communal.constant.AppConstants.OIDC_CONFIG_CACHE_NAME;


@Repository
@CacheConfig(cacheNames = {OIDC_CONFIG_CACHE_NAME})
public interface AppOidcConfigRepository extends JpaRepository<AppOidcConfigEntity, String> {
    // Delete by application ID
    // Parameter appId: String
    @CacheEvict(allEntries = true)
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    void deleteByAppId(@Param("appId") String appId);

    // delete
    // Parameter id: must not be null.
    @CacheEvict(allEntries = true)
    @Override
    void deleteById(@NotNull String id);

    // save
    // Parameter entity: must not be null.
    // Parameter <S>: S
    // Returns: AppOidcConfigEntity
    @NotNull
    @Override
    @CacheEvict(allEntries = true)
    <S extends AppOidcConfigEntity> S save(@NotNull S entity);

    // Get configuration by application ID
    // Parameter appId: String
    // Returns: AppOidcConfigEntity
    Optional<AppOidcConfigEntity> findByAppId(String appId);

    // Get by application ID
    // Parameter appId: String
    // Returns: AppOidcConfigPO
    @Query(value = "SELECT NEW cn.tolim.pudans.iam.communal.entity.app.po.AppOidcConfigPO(oc,a.code,a.name,a.template,a.clientId,a.clientSecret,a.initLoginUrl,a.authorizationType,a.enabled,a.configured) FROM AppEntity a INNER JOIN AppOidcConfigEntity oc ON a.id = oc.appId WHERE a.id =:appId")
    AppOidcConfigPO getByAppId(String appId);

    // Get by application client
    // Parameter clientId: String
    // Returns: AppOidcConfigPO
    @Query(value = "SELECT NEW cn.tolim.pudans.iam.communal.entity.app.po.AppOidcConfigPO(oc,a.code,a.name,a.template,a.clientId,a.clientSecret,a.initLoginUrl,a.authorizationType,a.enabled,a.configured) FROM AppEntity a INNER JOIN AppOidcConfigEntity oc ON a.id = oc.appId WHERE a.clientId =:clientId")
    AppOidcConfigPO getByClientId(String clientId);

    // Query application configuration by application code
    // Parameter appCode: String
    // Returns: AppOidcConfigPO
    @Query(value = "SELECT NEW cn.tolim.pudans.iam.communal.entity.app.po.AppOidcConfigPO(oc,a.code,a.name,a.template,a.clientId,a.clientSecret,a.initLoginUrl,a.authorizationType,a.enabled,a.configured) FROM AppEntity a INNER JOIN AppOidcConfigEntity oc ON a.id = oc.appId WHERE a.code =:appCode")
    AppOidcConfigPO findByAppCode(String appCode);
}
