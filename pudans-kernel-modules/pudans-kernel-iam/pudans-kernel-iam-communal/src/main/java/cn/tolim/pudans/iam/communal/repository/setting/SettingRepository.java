package cn.tolim.pudans.iam.communal.repository.setting;

import cn.tolim.pudans.iam.communal.entity.setting.SettingEntity;
import cn.tolim.pudans.iam.infra.context.ApplicationContextService;
import cn.tolim.pudans.iam.infra.util.BeanUtils;
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
import java.util.Objects;

import static cn.tolim.pudans.iam.communal.constant.SettingConstants.SETTING_CACHE_NAME;
import static cn.tolim.pudans.iam.infra.repository.base.BaseEntity.LAST_MODIFIED_BY;
import static cn.tolim.pudans.iam.infra.repository.base.BaseEntity.LAST_MODIFIED_TIME;


@Repository
@CacheConfig(cacheNames = {SETTING_CACHE_NAME})
public interface SettingRepository extends JpaRepository<SettingEntity, String> {
    // Returns the setting with the given name.
    @Cacheable(key = "#p0", unless = "#result==null")
    SettingEntity findByName(String name);

    // Returns settings whose names contain the given value.
    List<SettingEntity> findByNameLike(String name);

    // Returns settings with names in the given list.
    List<SettingEntity> findByNameIn(List<String> names);

    // Deletes the setting with the given ID.
    @Override
    @CacheEvict(allEntries = true)
    void deleteById(@NotNull String id);

    // Delete all
    @Override
    @CacheEvict(allEntries = true)
    void deleteAll();

    // Delete by name
    // Parameter name: String
    @Modifying
    @CacheEvict(allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    void deleteByName(@Param("name") String name);

    // Save configuration
    // Parameter list: List
    // Returns: Boolean
    default Boolean save(List<SettingEntity> list) {
        for (SettingEntity setting : list) {
            SettingEntity type = getSelf().findByName(setting.getName());
            if (Objects.isNull(type)) {
                save(setting);
                continue;
            }
            BeanUtils.merge(setting, type, LAST_MODIFIED_BY, LAST_MODIFIED_TIME);
            save(type);
        }
        return true;
    }

    // Saves a setting and returns the saved instance.
    @NotNull
    @Override
    @CacheEvict(allEntries = true)
    <S extends SettingEntity> S save(@NotNull S entity);

    default SettingRepository getSelf() {
        return ApplicationContextService.getBean(SettingRepository.class);
    }
}
