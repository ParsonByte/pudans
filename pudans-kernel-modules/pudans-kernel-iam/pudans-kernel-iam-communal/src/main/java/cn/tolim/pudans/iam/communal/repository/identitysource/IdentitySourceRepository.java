package cn.tolim.pudans.iam.communal.repository.identitysource;

import cn.tolim.pudans.iam.communal.constant.AccountConstants;
import cn.tolim.pudans.iam.communal.entity.identitysource.IdentitySourceEntity;
import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;


@Repository
@CacheConfig(cacheNames = {AccountConstants.IDS_CACHE_NAME})
public interface IdentitySourceRepository extends JpaRepository<IdentitySourceEntity, String>,
        JpaSpecificationExecutor<IdentitySourceEntity> {
    // Returns the identity source with the given ID, if present.
    @NotNull
    @Override
    @Cacheable(key = "#p0", unless = "#result==null")
    Optional<IdentitySourceEntity> findById(@NotNull @Param(value = "id") String id);

    // Returns all enabled identity sources.
    @Cacheable
    List<IdentitySourceEntity> findByEnabledIsTrue();

    // Updates an identity source's enabled status and returns the affected row count.
    @Transactional(rollbackFor = Exception.class)
    @Modifying
    @CacheEvict(allEntries = true)
    @Query(value = "UPDATE IdentitySourceEntity SET enabled=:enabled where id=:id")
    Integer updateIdentitySourceStatus(@Param(value = "id") String id,
                                       @Param(value = "enabled") Boolean enabled);

    // Saves an identity source and returns the saved instance.
    @Override
    @NonNull
    @CacheEvict(allEntries = true)
    <S extends IdentitySourceEntity> S save(@NonNull S entity);

    // Deletes the identity source with the given ID.
    @Override
    @CacheEvict(allEntries = true)
    void deleteById(@NonNull String id);

    // Updates the strategy configuration of the given identity source.
    @Transactional(rollbackFor = Exception.class)
    @Modifying
    @CacheEvict(allEntries = true)
    @Query(value = "UPDATE IdentitySourceEntity SET strategyConfig = :strategyConfig where id = :id")
    void updateStrategyConfig(@Param(value = "id") String id,
                              @Param(value = "strategyConfig") String strategyConfig);

    // Returns the identity source with the given code, if present.
    Optional<IdentitySourceEntity> findByCode(String code);
}
