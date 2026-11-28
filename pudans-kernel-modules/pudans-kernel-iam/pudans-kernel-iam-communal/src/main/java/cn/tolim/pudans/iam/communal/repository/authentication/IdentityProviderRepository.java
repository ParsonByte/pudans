package cn.tolim.pudans.iam.communal.repository.authentication;

import cn.tolim.pudans.iam.communal.entity.authn.IdentityProviderEntity;
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


@Repository
@CacheConfig(cacheNames = "idp")
public interface IdentityProviderRepository extends JpaRepository<IdentityProviderEntity, String>,
        JpaSpecificationExecutor<IdentityProviderEntity> {
    // Returns identity provider configurations for the given platform type.
    List<IdentityProviderEntity> findByType(String type);

    // Returns enabled identity providers that are displayed.
    List<IdentityProviderEntity> findByEnabledIsTrueAndDisplayedIsTrue();

    // Returns all enabled identity providers.
    List<IdentityProviderEntity> findByEnabledIsTrue();

    // Saves an identity provider and returns the saved instance.
    @NotNull
    @Override
    @CacheEvict(allEntries = true)
    <S extends IdentityProviderEntity> S save(@NotNull S entity);

    // Deletes the identity provider with the given ID.
    @Override
    @CacheEvict(allEntries = true)
    void deleteById(@NotNull String id);

    // Returns the identity provider with the given ID, if present.
    @NotNull
    @Override
    @Cacheable(key = "#a0")
    Optional<IdentityProviderEntity> findById(@NotNull @Param(value = "id") String id);

    // Updates an identity provider's enabled status and returns the affected row count.
    @Transactional(rollbackFor = Exception.class)
    @Modifying
    @CacheEvict(allEntries = true)
    @Query(value = "UPDATE IdentityProviderEntity SET enabled=:enabled where id=:id")
    Integer updateIdentityProviderStatus(@Param(value = "id") String id,
                                         @Param(value = "enabled") Boolean enabled);

    // Returns the enabled identity provider with the given ID, if present.
    @Cacheable(key = "#a0", unless = "#result == null")
    Optional<IdentityProviderEntity> findByIdAndEnabledIsTrue(String id);

    // Returns the enabled identity provider with the given code, if present.
    @Cacheable(key = "#a0", unless = "#result == null")
    Optional<IdentityProviderEntity> findByCodeAndEnabledIsTrue(String code);
}
