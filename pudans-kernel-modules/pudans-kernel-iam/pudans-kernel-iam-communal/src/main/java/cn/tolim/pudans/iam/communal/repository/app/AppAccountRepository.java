package cn.tolim.pudans.iam.communal.repository.app;

import cn.tolim.pudans.iam.communal.entity.app.AppAccountEntity;
import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static cn.tolim.pudans.iam.communal.constant.AppConstants.APP_ACCOUNT_CACHE_NAME;


@Repository
@CacheConfig(cacheNames = {APP_ACCOUNT_CACHE_NAME})
public interface AppAccountRepository extends JpaRepository<AppAccountEntity, String>,
        AppAccountRepositoryCustomized {
    // Saves an application account and returns the saved instance.
    @NotNull
    @Override
    @CacheEvict(allEntries = true)
    <S extends AppAccountEntity> S save(@NotNull S entity);

    // delete
    // Parameter id: must not be null.
    @CacheEvict(allEntries = true)
    @Override
    void deleteById(@NotNull String id);

    // Returns all application accounts for the given application and user.
    @Cacheable(key = "#p0+':'+#p1", unless = "#result == null || #result.isEmpty()")
    List<AppAccountEntity> findByAppIdAndUserId(String appId, String userId);

    // Returns the default application account for the given application and user.
    @Cacheable(key = "'default:'+#p0+':'+#p1", unless = "#result==null")
    Optional<AppAccountEntity> findByAppIdAndUserIdAndDefaultedIsTrue(String appId, String userId);

    // Returns the application account matching the application, user, and account name.
    @Cacheable(key = "#p0+':'+#p1+':'+#p2", unless = "#result==null")
    Optional<AppAccountEntity> findByAppIdAndUserIdAndAccount(String appId, String userId,
                                                              String account);

    // Delete all data by app ID
    // Parameter appId: String
    @CacheEvict(allEntries = true)
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    void deleteAllByAppId(@Param("appId") String appId);

    // Delete user data by user ID
    // Parameter userId: String
    @CacheEvict(allEntries = true)
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    void deleteByUserId(@Param("userId") String userId);
}
