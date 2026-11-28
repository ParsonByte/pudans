package cn.tolim.pudans.iam.communal.repository.app;

import cn.tolim.pudans.iam.communal.entity.app.AppEntity;
import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static cn.tolim.pudans.iam.communal.constant.AppConstants.APP_CACHE_NAME;


@Repository
@CacheConfig(cacheNames = {APP_CACHE_NAME})
public interface AppRepository extends JpaRepository<AppEntity, String>,
        JpaSpecificationExecutor<AppEntity>, AppRepositoryCustomized {

    // Saves an application and returns the saved instance.
    @NotNull
    @Override
    @CacheEvict(allEntries = true)
    <S extends AppEntity> S save(@NotNull S entity);

    // Updates an application's enabled status and returns the affected row count.
    @Modifying
    @CacheEvict(allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    @Query(value = "UPDATE AppEntity SET enabled = :enabled WHERE id = :id")
    Integer updateAppStatus(@Param(value = "id") String id,
                            @Param(value = "enabled") Boolean enabled);

    // delete
    // Parameter id: must not be null.
    @Override
    @CacheEvict(allEntries = true)
    void deleteById(@NotNull String id);

    // Returns the application with the given ID, if present.
    @NotNull
    @Override
    @Cacheable(key = "#p0", unless = "#result==null")
    Optional<AppEntity> findById(@NotNull @Param(value = "id") String id);

    // Returns the application with the given client ID, if present.
    @Cacheable(key = "#p0", unless = "#result==null")
    Optional<AppEntity> findByClientId(String clientId);

    // Returns the application with the given code, if present.
    @NotNull
    @Cacheable(key = "#p0", unless = "#result==null")
    Optional<AppEntity> findByCode(String appCode);

    // Returns applications with the given IDs.
    @NotNull
    List<AppEntity> findByIdIn(List<String> appIds);
}
