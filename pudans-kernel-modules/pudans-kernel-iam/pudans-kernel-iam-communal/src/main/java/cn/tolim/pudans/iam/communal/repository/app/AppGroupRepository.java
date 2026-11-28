package cn.tolim.pudans.iam.communal.repository.app;

import cn.tolim.pudans.iam.communal.entity.app.AppGroupEntity;
import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static cn.tolim.pudans.iam.communal.constant.AppConstants.APP_GROUP_CACHE_NAME_PREFIX;


@Repository
@CacheConfig(cacheNames = {APP_GROUP_CACHE_NAME_PREFIX})
public interface AppGroupRepository extends JpaRepository<AppGroupEntity, String>,
        AppGroupRepositoryCustomized {

    // save
    // Parameter entity: must not be null.
    // Parameter <S>: S
    // Returns: AppGroupEntity
    @NotNull
    @Override
    @CacheEvict(allEntries = true)
    <S extends AppGroupEntity> S save(@NotNull S entity);

    // delete
    // Parameter id: must not be null.
    @Override
    @CacheEvict(allEntries = true)
    void deleteById(@NotNull String id);

    // find by id
    // Parameter id: must not be null.
    // Returns: AppGroupEntity
    @NotNull
    @Override
    Optional<AppGroupEntity> findById(@NotNull String id);

    // Get all group lists
    // Returns: List
    @Query(value = "FROM AppGroupEntity")
    List<AppGroupEntity> getAppGroupList();

    // Query by code
    // Parameter code: String
    // Returns: AppGroupEntity
    Optional<AppGroupEntity> findByCode(@Param("code") String code);

    // Check code uniqueness
    // Parameter code: String
    // Returns: boolean
    boolean existsByCode(String code);
}
