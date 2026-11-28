package cn.tolim.pudans.iam.communal.repository.account;

import cn.tolim.pudans.iam.communal.entity.account.UserEntity;
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
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static cn.tolim.pudans.iam.communal.constant.AccountConstants.USER_CACHE_NAME;


@Repository
@CacheConfig(cacheNames = {USER_CACHE_NAME})
public interface UserRepository extends JpaRepository<UserEntity, String>,
        JpaSpecificationExecutor<UserEntity>, UserRepositoryCustomized {
    // findById
    // Parameter id: must not be null.
    // Returns: UserEntity
    @NotNull
    @Override
    @Cacheable(key = "#p0", unless = "#result==null")
    Optional<UserEntity> findById(@NotNull @Param(value = "id") String id);

    // findById
    // Parameter id: must not be null.
    @Override
    @CacheEvict(allEntries = true)
    void deleteById(@NotNull String id);

    // findById
    // Parameter ids: must not be null.
    @Override
    @CacheEvict(allEntries = true)
    void deleteAllById(@NotNull Iterable<? extends String> ids);

    // save
    // Parameter entity: must not be null.
    // Returns: UserEntity
    // Parameter <S>: UserEntity
    @Override
    @CacheEvict(allEntries = true)
    <S extends UserEntity> S save(@NotNull S entity);

    // Query user information by username
    // Parameter username: String
    // Returns: UserEntity
    Optional<UserEntity> findByUsername(String username);

    // Query user information by phone number
    // Parameter phone: String
    // Returns: UserEntity
    Optional<UserEntity> findByPhone(String phone);

    // Query user information by email
    // Parameter email: String
    // Returns: UserEntity
    Optional<UserEntity> findByEmail(String email);

    // Query user information by external ID
    // Parameter id: String
    // Returns: UserEntity
    Optional<UserEntity> findByExternalId(String id);

    // Query user information by external ID
    // Parameter ids: Collection
    // Returns: UserEntity
    List<UserEntity> findByExternalIdIn(Collection<String> ids);

    // Update user password
    // Parameter id: String
    // Parameter password: String
    // Parameter lastUpdatePasswordTime: LocalDateTime
    // Returns: Integer
    @Transactional(rollbackFor = Exception.class)
    @Modifying
    @CacheEvict(allEntries = true)
    @Query(value = "update UserEntity set password =:password,lastUpdatePasswordTime = :lastUpdatePasswordTime where id=:id")
    Integer updatePassword(@Param(value = "id") String id,
                           @Param(value = "password") String password,
                           @Param(value = "lastUpdatePasswordTime") LocalDateTime lastUpdatePasswordTime);

    // Update user email
    // Parameter id: String
    // Parameter email: String
    // Returns: Integer
    @Transactional(rollbackFor = Exception.class)
    @Modifying
    @CacheEvict(allEntries = true)
    @Query(value = "UPDATE UserEntity SET email=:email WHERE id=:id")
    Integer updateByIdAndEmail(@Param(value = "id") String id,
                               @Param(value = "email") String email);

    // Update user phone number
    // Parameter id: String
    // Parameter phone: String
    // Returns: Integer
    @Transactional(rollbackFor = Exception.class)
    @Modifying
    @CacheEvict(allEntries = true)
    @Query(value = "UPDATE UserEntity SET phone=:phone WHERE id=:id")
    Integer updateByIdAndPhone(@Param(value = "id") String id,
                               @Param(value = "phone") String phone);

    // Update user status
    // Parameter id: String
    // Parameter status: UserStatus
    // Returns: Integer
    @Transactional(rollbackFor = Exception.class)
    @Modifying
    @CacheEvict(allEntries = true)
    @Query(value = "update UserEntity set status=:status where id=:id")
    Integer updateUserStatus(@Param(value = "id") String id,
                             @Param(value = "status") UserStatus status);

    // Query expired users
    // Returns: UserEntity
    @Query(value = "FROM UserEntity WHERE expireDate <= CURRENT_DATE and status != 'expired_locked'")
    List<UserEntity> findExpireUser();

    // Query all by username
    // Parameter usernames: String
    // Returns: List
    List<UserEntity> findAllByUsernameIn(@Param("usernames") Collection<String> usernames);

    // Query all by phone number
    // Parameter phones: String
    // Returns: List
    List<UserEntity> findAllByPhoneIn(@Param("phones") Collection<String> phones);

    // Fuzzy query by email
    // Parameter emails: String
    // Returns: List
    List<UserEntity> findAllByEmailIn(@Param("emails") Collection<String> emails);

    // Fuzzy query by status
    // Parameter status: UserStatus
    // Returns: List
    List<UserEntity> findAllByStatusIn(Collection<UserStatus> status);

    // Query by status
    // Parameter status: UserStatus
    // Returns: List
    List<UserEntity> findAllByStatus(UserStatus status);

    // Find IDs not in the given ID set
    // Parameter ids: Collection
    // Parameter dataOrigin: String
    // Returns: List
    List<UserEntity> findAllByIdNotInAndDataOrigin(Collection<String> ids, String dataOrigin);

    // Query user list by identity source ID
    // Parameter identitySourceId: String
    // Returns: List
    List<UserEntity> findAllByIdentitySourceId(String identitySourceId);

    // Update successful authentication information
    // Parameter id: String
    // Parameter ip: String
    // Parameter loginTime: LocalDateTime
    @CacheEvict(allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    @Modifying
    @Query(value = "UPDATE UserEntity SET authTotal = (COALESCE(authTotal,0) +1),lastAuthIp = :ip,lastAuthTime = :loginTime WHERE id = :id")
    void updateAuthSucceedInfo(@Param("id") String id, @Param("ip") String ip,
                               @Param("loginTime") LocalDateTime loginTime);

    // Query user count by status
    // Parameter status: UserStatus
    // Returns: Long
    Long countByStatus(UserStatus status);

    // Fuzzy query by full name
    // Parameter fullName: String
    // Returns: UserEntity
    List<UserEntity> findByFullNameLike(String fullName);
}
