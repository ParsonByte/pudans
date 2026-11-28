package cn.tolim.pudans.iam.communal.repository.app;

import cn.tolim.pudans.iam.communal.entity.app.AppJwtConfigEntity;
import cn.tolim.pudans.iam.communal.entity.app.po.AppJwtConfigPO;
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

import static cn.tolim.pudans.iam.communal.constant.AppConstants.JWT_CONFIG_CACHE_NAME;


@Repository
@CacheConfig(cacheNames = {JWT_CONFIG_CACHE_NAME})
public interface AppJwtConfigRepository extends JpaRepository<AppJwtConfigEntity, String> {
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

    // Saves a JWT configuration and returns the saved instance.
    @NotNull
    @Override
    @CacheEvict(allEntries = true)
    <S extends AppJwtConfigEntity> S save(@NotNull S entity);

    // Returns the JWT configuration for the given application ID, if present.
    Optional<AppJwtConfigEntity> findByAppId(String appId);

    // Returns the JWT configuration projection for the given application ID.
    @Query(value = "SELECT NEW cn.tolim.pudans.iam.communal.entity.app.po.AppJwtConfigPO(jc,a.code,a.name,a.template,a.clientId,a.clientSecret,a.initLoginUrl,a.authorizationType,a.enabled,a.configured) FROM AppEntity a INNER JOIN AppJwtConfigEntity jc ON a.id = jc.appId WHERE a.id =:appId")
    AppJwtConfigPO getByAppId(String appId);

    // Returns the JWT configuration projection for the given application code.
    @Query(value = "SELECT NEW cn.tolim.pudans.iam.communal.entity.app.po.AppJwtConfigPO(jc,a.code,a.name,a.template,a.clientId,a.clientSecret,a.initLoginUrl,a.authorizationType,a.enabled,a.configured) FROM AppEntity a INNER JOIN AppJwtConfigEntity jc ON a.id = jc.appId WHERE a.code =:appCode")
    AppJwtConfigPO findByAppCode(String appCode);
}
