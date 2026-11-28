package cn.tolim.pudans.iam.communal.repository.setting;

import cn.tolim.pudans.iam.communal.entity.setting.AdministratorEntity;
import cn.tolim.pudans.iam.communal.enums.UserStatus;
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

import java.time.LocalDateTime;
import java.util.Optional;

import static cn.tolim.pudans.iam.communal.constant.SettingConstants.ADMIN_CACHE_NAME;


@Repository
@CacheConfig(cacheNames = {ADMIN_CACHE_NAME})
public interface AdministratorRepository extends JpaRepository<AdministratorEntity, String>,
        JpaSpecificationExecutor<AdministratorEntity> {

    // Returns the administrator with the given ID, if present.
    @NotNull
    @Override
    @Cacheable
    Optional<AdministratorEntity> findById(@NotNull @Param(value = "id") String id);

    // Deletes the administrator with the given ID.
    @Override
    @CacheEvict(allEntries = true)
    void deleteById(@NotNull String id);

    // Deletes administrators with the given IDs.
    @Override
    @CacheEvict(allEntries = true)
    void deleteAllById(@NotNull Iterable<? extends String> ids);

    // Saves an administrator and returns the saved instance.
    @Override
    @CacheEvict(allEntries = true)
    <S extends AdministratorEntity> S save(@NotNull S entity);

    // Returns the administrator with the given username, if present.
    Optional<AdministratorEntity> findByUsername(String username);

    // Returns the administrator with the given phone number, if present.
    Optional<AdministratorEntity> findByPhone(String phone);

    // Returns the administrator with the given email, if present.
    Optional<AdministratorEntity> findByEmail(String email);

    // Updates the status of the given administrator.
    @Transactional(rollbackFor = Exception.class)
    @Modifying
    @CacheEvict(allEntries = true)
    @Query(value = "UPDATE AdministratorEntity set status = :status where id = :id")
    void updateStatus(@Param(value = "id") String id, @Param(value = "status") UserStatus status);

    // Update user password
    // Parameter id: String
    // Parameter password: String
    // Parameter lastUpdatePasswordTime: LocalDateTime
    // Returns: Integer
    @Transactional(rollbackFor = Exception.class)
    @Modifying
    @CacheEvict(allEntries = true)
    @Query(value = "update AdministratorEntity set password =:password, lastUpdatePasswordTime = :lastUpdatePasswordTime where id=:id")
    Integer updatePassword(@Param(value = "id") String id,
                           @Param(value = "password") String password,
                           @Param(value = "lastUpdatePasswordTime") LocalDateTime lastUpdatePasswordTime);

    // Update successful authentication information
    // Parameter id: String
    // Parameter ip: String
    // Parameter loginTime: LocalDateTime
    @CacheEvict(allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    @Modifying
    @Query(value = "UPDATE AdministratorEntity SET authTotal = (COALESCE(authTotal,0) +1),lastAuthIp = :ip,lastAuthTime = :loginTime WHERE id = :id")
    void updateAuthSucceedInfo(@Param("id") String id, @Param("ip") String ip,
                               @Param("loginTime") LocalDateTime loginTime);

    // Updates an administrator's email and returns the affected row count.
    @Transactional(rollbackFor = Exception.class)
    @Modifying
    @CacheEvict(allEntries = true)
    @Query(value = "UPDATE AdministratorEntity SET email =:email WHERE id=:id")
    Integer updateByIdAndEmail(@Param(value = "id") String id,
                               @Param(value = "email") String email);

    // Updates an administrator's phone number and returns the affected row count.
    @Transactional(rollbackFor = Exception.class)
    @Modifying
    @CacheEvict(allEntries = true)
    @Query(value = "UPDATE AdministratorEntity set phone =:phone where id=:id")
    Integer updateByIdAndPhone(@Param(value = "id") String id,
                               @Param(value = "phone") String phone);
}
