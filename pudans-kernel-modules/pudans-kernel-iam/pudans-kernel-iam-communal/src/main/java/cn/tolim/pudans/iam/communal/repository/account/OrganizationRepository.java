package cn.tolim.pudans.iam.communal.repository.account;

import cn.tolim.pudans.iam.communal.entity.account.OrganizationEntity;
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

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static cn.tolim.pudans.iam.communal.constant.AccountConstants.ORG_CACHE_NAME;


@Repository
@CacheConfig(cacheNames = {ORG_CACHE_NAME})
public interface OrganizationRepository extends JpaRepository<OrganizationEntity, String>,
        JpaSpecificationExecutor<OrganizationEntity>,
        OrganizationRepositoryCustomized {

    // Returns the number of organizations with the given name.
    Long countByName(String s);

    // Returns the organization with the given name.
    OrganizationEntity findByName(String name);

    // Returns the child organizations of the given parent.
    @Cacheable(key = "'child:'+#p0", unless = "#result==null")
    List<OrganizationEntity> findByParentId(String parentId);

    // Returns the child organizations of the given parent ordered by sort value.
    @Cacheable(key = "'child_asc:'+#p0", unless = "#result==null || #result.isEmpty()")
    List<OrganizationEntity> findByParentIdOrderByOrderAsc(String parentId);

    // findById
    // Parameter id: String
    // Returns: Optional
    @NotNull
    @Override
    @Cacheable(key = "#p0", unless = "#result==null")
    Optional<OrganizationEntity> findById(@NotNull String id);

    // deleteById
    // Parameter id: String
    @Override
    @CacheEvict(allEntries = true)
    void deleteById(@NotNull String id);

    // Move organization
    // Parameter id: String
    // Parameter parentId: String
    @Transactional(rollbackFor = Exception.class)
    @Modifying
    @CacheEvict(allEntries = true)
    @Query(value = "UPDATE OrganizationEntity SET parentId =:parentId  WHERE id =:id")
    void moveOrganization(@Param(value = "id") String id,
                          @Param(value = "parentId") String parentId);

    // Update leaf node
    // Parameter id: String
    // Parameter isLeaf: Boolean
    @Transactional(rollbackFor = Exception.class)
    @Modifying
    @CacheEvict(allEntries = true)
    @Query(value = "UPDATE OrganizationEntity set leaf =:isLeaf WHERE id =:id")
    void updateIsLeaf(@Param(value = "id") String id, @Param(value = "isLeaf") Boolean isLeaf);

    // Updates the enabled status and returns the affected row count.
    @Modifying
    @CacheEvict(allEntries = true)
    @Query(value = "UPDATE OrganizationEntity set enabled =:status WHERE id =:id")
    Integer updateStatus(@Param(value = "id") String id, @Param(value = "status") Boolean status);

    // Returns organizations whose name or code matches the keyword.
    @Query(value = "FROM OrganizationEntity WHERE name LIKE :keyWord OR code LIKE :keyWord")
    List<OrganizationEntity> findByNameLikeOrCodeLike(@Param(value = "keyWord") String keyWord);

    // Returns organizations with the given IDs, newest first.
    List<OrganizationEntity> findByIdInOrderByCreateTimeDesc(Collection<String> id);

    // Returns organizations matching the identity source and external department IDs.
    List<OrganizationEntity> findByIdentitySourceIdAndExternalIdIn(String identitySourceId,
                                                                   List<String> deptIdList);

    // Returns organizations matching the identity source and internal organization IDs.
    List<OrganizationEntity> findByIdentitySourceIdAndIdIn(String identitySourceId,
                                                           List<String> orgIdList);

    // Returns the organization with the given external ID, if present.
    Optional<OrganizationEntity> findByExternalId(String deptId);

    // Returns the organization matching the external ID and identity source.
    OrganizationEntity findByExternalIdAndIdentitySourceId(String externalId,
                                                           String identitySourceId);

    // Returns organizations matching the external IDs and identity source.
    List<OrganizationEntity> findByExternalIdInAndIdentitySourceId(List<String> externalId,
                                                                   String identitySourceId);

    // Returns child organizations for the parent, data origin, and identity source.
    List<OrganizationEntity> findByParentIdAndDataOriginAndIdentitySourceId(String parentId,
                                                                            String dataOrigin,
                                                                            String identitySourceId);

    // Returns all organizations for the given identity source.
    List<OrganizationEntity> findByIdentitySourceId(String identitySourceId);

    // Returns organizations with the given IDs ordered by sort value.
    List<OrganizationEntity> findByIdInOrderByOrderAsc(Collection<String> parentIds);

    // Saves an organization and returns the saved instance.
    @NotNull
    @Override
    @CacheEvict(allEntries = true)
    <S extends OrganizationEntity> S save(@NotNull S entity);

    // Deletes all organizations with the given IDs.
    @CacheEvict(allEntries = true)
    @Override
    void deleteAllById(@NotNull Iterable<? extends String> ids);

    // Returns user IDs of members in the given organization or its descendants.
    @Query(value = """
                SELECT
                    user.id
                FROM
                    UserEntity user
                    INNER JOIN OrganizationMemberEntity om ON user.id = om.userId
                    INNER JOIN OrganizationEntity organization ON organization.id = om.orgId
                WHERE
                    organization.id = :orgId OR LOCATE(:orgId, organization.path) > 0
            """)
    List<String> getOrgMemberList(@Param("orgId") String orgId);
}
