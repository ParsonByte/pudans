package cn.tolim.pudans.iam.communal.repository.app;

import cn.tolim.pudans.iam.communal.entity.app.AppFormConfigEntity;
import cn.tolim.pudans.iam.communal.entity.app.po.AppFormConfigPO;
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

import static cn.tolim.pudans.iam.communal.constant.AppConstants.FORM_CONFIG_CACHE_NAME;


@Repository
@CacheConfig(cacheNames = {FORM_CONFIG_CACHE_NAME})
public interface AppFormConfigRepository extends JpaRepository<AppFormConfigEntity, String> {
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
    // Returns: AppFormConfigEntity
    @NotNull
    @Override
    @CacheEvict(allEntries = true)
    <S extends AppFormConfigEntity> S save(@NotNull S entity);

    // Get configuration by application ID
    // Parameter appId: String
    // Returns: AppFormConfigEntity
    Optional<AppFormConfigEntity> findByAppId(String appId);

    // Get by application ID
    // Parameter appId: String
    // Returns: AppFormConfigPO
    @Query(value = "SELECT NEW cn.tolim.pudans.iam.communal.entity.app.po.AppFormConfigPO(fc,a.code,a.name,a.template,a.clientId,a.clientSecret,a.initLoginUrl,a.authorizationType,a.enabled,a.configured) FROM AppEntity a INNER JOIN AppFormConfigEntity fc ON a.id = fc.appId WHERE a.id =:appId")
    AppFormConfigPO getByAppId(String appId);

    // Get by application client
    // Parameter clientId: String
    // Returns: AppFormConfigPO
    @Query(value = "SELECT NEW cn.tolim.pudans.iam.communal.entity.app.po.AppFormConfigPO(fc,a.code,a.name,a.template,a.clientId,a.clientSecret,a.initLoginUrl,a.authorizationType,a.enabled,a.configured) FROM AppEntity a INNER JOIN AppFormConfigEntity fc ON a.id = fc.appId WHERE a.clientId =:clientId")
    AppFormConfigPO getByClientId(String clientId);

    // Query application configuration by application code
    // Parameter appCode: String
    // Returns: AppFormConfigPO
    @Query(value = "SELECT NEW cn.tolim.pudans.iam.communal.entity.app.po.AppFormConfigPO(fc,a.code,a.name,a.template,a.clientId,a.clientSecret,a.initLoginUrl,a.authorizationType,a.enabled,a.configured) FROM AppEntity a INNER JOIN AppFormConfigEntity fc ON a.id = fc.appId WHERE a.code =:appCode")
    AppFormConfigPO findByAppCode(String appCode);
}
