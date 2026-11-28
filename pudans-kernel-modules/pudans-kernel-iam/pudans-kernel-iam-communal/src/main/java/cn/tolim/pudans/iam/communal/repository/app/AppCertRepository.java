package cn.tolim.pudans.iam.communal.repository.app;

import cn.tolim.pudans.iam.communal.entity.app.AppCertEntity;
import cn.tolim.pudans.iam.communal.enums.app.AppCertUsingType;
import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static cn.tolim.pudans.iam.communal.constant.AppConstants.APP_CERT_CACHE_NAME;


@CacheConfig(cacheNames = {APP_CERT_CACHE_NAME})
public interface AppCertRepository extends JpaRepository<AppCertEntity, String> {
    // Returns the certificate for the given application and usage type, if present.
    @Cacheable(key = "#p1.code+':'+#p0", unless = "#result==null")
    Optional<AppCertEntity> findByAppIdAndUsingType(String appId, AppCertUsingType usingType);

    // Saves an application certificate and returns the saved instance.
    @NotNull
    @Override
    @CacheEvict(allEntries = true)
    <S extends AppCertEntity> S save(@NotNull S entity);

    // delete
    // Parameter id: must not be null.
    @CacheEvict(allEntries = true)
    @Override
    void deleteById(@NotNull String id);

    // Deletes certificates for the given application ID.
    @CacheEvict(allEntries = true)
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    void deleteByAppId(@Param("appId") String appId);

    // Returns the application certificate with the given ID, if present.
    @NotNull
    @Override
    @Cacheable
    Optional<AppCertEntity> findById(@NotNull String id);
}
