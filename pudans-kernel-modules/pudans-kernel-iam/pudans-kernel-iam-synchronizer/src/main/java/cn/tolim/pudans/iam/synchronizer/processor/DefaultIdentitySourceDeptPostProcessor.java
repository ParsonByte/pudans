package cn.tolim.pudans.iam.synchronizer.processor;

import cn.tolim.pudans.iam.communal.entity.account.OrganizationEntity;
import cn.tolim.pudans.iam.communal.entity.identitysource.IdentitySourceEntity;
import cn.tolim.pudans.iam.communal.entity.identitysource.IdentitySourceSyncHistoryEntity;
import cn.tolim.pudans.iam.communal.entity.identitysource.IdentitySourceSyncRecordEntity;
import cn.tolim.pudans.iam.communal.entity.identitysource.config.StrategyConfig;
import cn.tolim.pudans.iam.communal.enums.SyncStatus;
import cn.tolim.pudans.iam.communal.enums.TriggerType;
import cn.tolim.pudans.iam.communal.enums.account.OrganizationType;
import cn.tolim.pudans.iam.communal.enums.identitysource.IdentitySourceActionType;
import cn.tolim.pudans.iam.communal.enums.identitysource.IdentitySourceObjectType;
import cn.tolim.pudans.iam.communal.repository.account.OrganizationRepository;
import cn.tolim.pudans.iam.communal.repository.identitysource.IdentitySourceRepository;
import cn.tolim.pudans.iam.communal.repository.identitysource.IdentitySourceSyncHistoryRepository;
import cn.tolim.pudans.iam.communal.repository.identitysource.IdentitySourceSyncRecordRepository;
import cn.tolim.pudans.iam.communal.storage.Storage;
import cn.tolim.pudans.iam.core.message.mail.MailMsgEventPublish;
import cn.tolim.pudans.iam.identitysource.core.domain.Dept;
import cn.tolim.pudans.iam.identitysource.core.processor.IdentitySourceSyncDeptPostProcessor;
import cn.tolim.pudans.iam.infra.repository.base.IdEntity;
import cn.tolim.pudans.iam.infra.security.password.PasswordGenerator;
import cn.tolim.pudans.iam.synchronizer.provider.IdentitySourceProviderResolver;
import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import jakarta.persistence.EntityManager;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StopWatch;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.tolim.pudans.iam.communal.constant.AccountConstants.ROOT_DEPT_ID;
import static cn.tolim.pudans.iam.infra.constant.IamConstants.*;
import static java.util.stream.Collectors.toList;


@SuppressWarnings("DuplicatedCode")
@Slf4j
@Component
public class DefaultIdentitySourceDeptPostProcessor extends AbstractIdentitySourcePostProcessor
        implements IdentitySourceSyncDeptPostProcessor {

    // EntityManager
    private final EntityManager entityManager;
    // Identity source repository
    private final IdentitySourceRepository identitySourceRepository;
    // Organization repository
    private final OrganizationRepository organizationRepository;

    public DefaultIdentitySourceDeptPostProcessor(MailMsgEventPublish mailMsgEventPublish,
                                                  PasswordEncoder passwordEncoder,
                                                  PasswordGenerator passwordGenerator,
                                                  TransactionDefinition transactionDefinition,
                                                  PlatformTransactionManager platformTransactionManager,
                                                  EntityManager entityManager,
                                                  IdentitySourceRepository identitySourceRepository,
                                                  OrganizationRepository organizationRepository,
                                                  IdentitySourceSyncHistoryRepository identitySourceSyncHistoryRepository,
                                                  IdentitySourceSyncRecordRepository identitySourceSyncRecordRepository,
                                                  Storage storage,
                                                  IdentitySourceProviderResolver identitySourceProviderResolver) {
        super(mailMsgEventPublish, passwordEncoder, passwordGenerator, transactionDefinition,
                platformTransactionManager, entityManager, identitySourceRepository,
                identitySourceSyncHistoryRepository, identitySourceSyncRecordRepository, storage,
                identitySourceProviderResolver);
        this.entityManager = entityManager;
        this.identitySourceRepository = identitySourceRepository;
        this.organizationRepository = organizationRepository;
    }

    @Override
    public void process(String batch, String identitySourceId, List<Dept> deptList,
                        LocalDateTime startTime, TriggerType triggerType) {
        // Save sync history
        IdentitySourceSyncHistoryEntity history = new IdentitySourceSyncHistoryEntity();
        history.setBatch(batch);
        history.setIdentitySourceId(identitySourceId);
        history.setStartTime(startTime);
        history.setObjectType(IdentitySourceObjectType.ORGANIZATION);
        history.setTriggerType(triggerType);
        history.setStatus(SyncStatus.PENDING);
        identitySourceSyncHistoryRepository.save(history);
        // Start transaction manually
        TransactionStatus transactionStatus = platformTransactionManager
                .getTransaction(transactionDefinition);
        try {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            log.info("处理组织机构数据开始");
            IdentitySourceEntity identitySource = getIdentitySource(identitySourceId);
            StrategyConfig strategyConfig = getStrategyConfig(identitySourceId);
            // Filter out the identity source root directory
            deptList = deptList.stream().filter(
                            item -> !getRootExternalId(identitySource).equals(item.getDeptId()))
                    .collect(toList());
            Statistics statistics = new Statistics();
            // Process organization parent node
            rootOrganizationHandler(identitySource, deptList, strategyConfig, statistics);
            // Process organization child nodes
            childrenOrganizationHandler(identitySource, deptList, strategyConfig, statistics);
            stopWatch.stop();
            log.info("处理组织机构数据结束, 执行时长: {} ms", stopWatch.getTotalTimeMillis());
            // Update sync history
            history.setUpdatedCount(statistics.getUpdateOrganizations().size());
            history.setCreatedCount(statistics.getCreateOrganizations().size());
            history.setSkippedCount(statistics.getSkipOrganizations().size());
            history.setDeletedCount(statistics.getDeleteOrganizations().size());
            history.setEndTime(LocalDateTime.now());
            history.setStatus(SyncStatus.SUCCESS);
            identitySourceSyncHistoryRepository.save(history);
            // Save sync record
            saveSyncHistoryRecord(history.getId(), statistics);
            // Commit transaction
            platformTransactionManager.commit(transactionStatus);
        } catch (Exception e) {
            log.error("处理组织机构数据发生异常", e);
            platformTransactionManager.rollback(transactionStatus);
            // Update sync history
            history.setEndTime(LocalDateTime.now());
            history.setStatus(SyncStatus.FAIL);
            identitySourceSyncHistoryRepository.save(history);
        }
    }

    // Process root organization
    private void rootOrganizationHandler(IdentitySourceEntity identitySource, List<Dept> deptList,
                                         StrategyConfig strategyConfig, Statistics statistics) {

        // Identity source has no existing organization selected
        if (Objects.isNull(strategyConfig.getOrganization())
                || StringUtils.isBlank(strategyConfig.getOrganization().getTargetId())) {
            // Create root organization
            String targetId = String.valueOf(UUID.randomUUID().toString());
            OrganizationEntity entity = createRootOrganization(targetId, identitySource,
                    CollectionUtils.isEmpty(deptList));
            statistics.getCreateOrganizations().add(entity);
            // Update identity source root organization configuration
            StrategyConfig.Organization organization = new StrategyConfig.Organization();
            organization.setTargetId(targetId);
            strategyConfig.setOrganization(organization);
            identitySourceRepository.updateStrategyConfig(identitySource.getId(),
                    JSON.toJSONString(strategyConfig));
            return;
        }
        // Query the root organization configured for the identity source; create it if missing
        String targetId = strategyConfig.getOrganization().getTargetId();
        Optional<OrganizationEntity> optional = organizationRepository.findById(targetId);
        if (optional.isEmpty()) {
            OrganizationEntity entity = createRootOrganization(targetId, identitySource,
                    CollectionUtils.isEmpty(deptList));
            statistics.getCreateOrganizations().add(entity);
            return;
        }
        // Root organization exists; update when fields differ
        OrganizationEntity entity = optional.get();
        // TODO: When multiple identity sources select one parent organization, should the external ID be updated? Not updating can cause user association problems.
        if (BooleanUtils.compare(entity.getLeaf(), CollectionUtils.isEmpty(deptList)) != 0
                || !StringUtils.equals(entity.getExternalId(),
                getRootExternalId(identitySource))) {
            entity.setLeaf(CollectionUtils.isEmpty(deptList));
            entity.setExternalId(getRootExternalId(identitySource));
            statistics.getUpdateOrganizations().add(entity);
            organizationRepository.save(entity);
        }
    }

    // Process child nodes
    // 1. Not upstream, not local: create
    // 2. Upstream, local: update
    // 3. Not upstream, local: delete
    private void childrenOrganizationHandler(IdentitySourceEntity identitySource,
                                             List<Dept> deptList, StrategyConfig strategyConfig,
                                             Statistics statistics) {
        Set<OrganizationEntity> createSet = Sets.newLinkedHashSet(), updateSet = Sets.newLinkedHashSet();
        Set<SkipOrganization> skipSet=Sets.newLinkedHashSet();
        // Query all data under the identity source
        List<OrganizationEntity> list = organizationRepository.findByIdentitySourceId(identitySource.getId());
        Optional<OrganizationEntity> optional = organizationRepository.findById(strategyConfig.getOrganization().getTargetId());
        if (optional.isEmpty()) {
            log.error("处理子组织数据失败，父组织不存在");
            throw new RuntimeException("处理子组织数据失败，父组织不存在");
        }
        // Build new associations
        Set<Modal> modals = buildModal(deptList, list);
        // Build objects to update or modify
        buildEntity(optional.get(), identitySource.getId(), modals, createSet, updateSet,skipSet);
        entityManager.clear();

        // Save
        if (!CollectionUtils.isEmpty(createSet)){
            organizationRepository.batchSave(new ArrayList<>(createSet));
            statistics.getCreateOrganizations().addAll(createSet);
        }
        // Modify
        if (!CollectionUtils.isEmpty(updateSet)){
            organizationRepository.batchUpdate(new ArrayList<>(updateSet));
            statistics.getUpdateOrganizations().addAll(updateSet);
        }
        // All third-party IDs for current departments
        // All upstream department IDs
        List<String> thirdPartyDeptIds = getDeptIds(deptList);
        // Filter departments to delete
        Set<OrganizationEntity> deleteOrganizations = list.stream().filter(item -> !thirdPartyDeptIds.contains(item.getExternalId())).collect(Collectors.toSet());
        deleteOrganizations = deleteOrganizations.stream().filter(i-> !i.getExternalId().equals(getRootExternalId(identitySource))).collect(Collectors.toSet());
        if (!CollectionUtils.isEmpty(deleteOrganizations)){
            organizationRepository.deleteAllById(deleteOrganizations.stream().map(IdEntity::getId).collect(toList()));
            statistics.getDeleteOrganizations().addAll(deleteOrganizations);
        }
        // Skip
        statistics.getSkipOrganizations().addAll(skipSet);
    }

    private List<String> getDeptIds(List<Dept> deptList) {
        List<String> ids = deptList.stream().map(Dept::getDeptId).distinct().collect(toList());
        deptList.forEach(item -> {
            if (!CollectionUtils.isEmpty(item.getChildren())) {
                List<String> deptIds = getDeptIds(item.getChildren());
                ids.addAll(deptIds);
            }
        });
        return ids;
    }

    // Build create and update data sets from the new model
    private void buildEntity(OrganizationEntity parent, String identitySourceId, Set<Modal> modals,
                             Set<OrganizationEntity> createSet, Set<OrganizationEntity> updateSet,
                             Set<SkipOrganization> skipSet) {
        for (Modal modal : modals) {
            Dept thirdParty = modal.getThirdParty();
            Set<Modal> children = modal.getChildren();
            IdentitySourceEntity identitySource = getIdentitySource(identitySourceId);
            // Build create data
            if (modal.isNew()) {
                OrganizationEntity entity = new OrganizationEntity();
                entity.setId(String.valueOf(UUID.randomUUID().toString()));
                entity.setName(thirdParty.getName());
                entity.setCode(RandomStringUtils.randomAlphanumeric(7));
                entity.setType(OrganizationType.DEPARTMENT);
                entity.setParentId(parent.getId());
                entity.setPath(parent.getPath() + PATH_SEPARATOR + entity.getId());
                entity.setDisplayPath(parent.getDisplayPath() + PATH_SEPARATOR + entity.getName());
                entity.setExternalId(thirdParty.getDeptId());
                entity.setDataOrigin(getDataOrigin(identitySource).getType());
                entity.setIdentitySourceId(identitySourceId);
                entity.setLeaf(CollectionUtils.isEmpty(children));
                entity.setEnabled(Boolean.TRUE);
                entity.setOrder(thirdParty.getOrder());
                entity.setCreateBy(SYSTEM_DEFAULT_USER_NAME);
                entity.setCreateTime(LocalDateTime.now());
                entity.setUpdateBy(SYSTEM_DEFAULT_USER_NAME);
                entity.setUpdateTime(LocalDateTime.now());
                createSet.add(entity);
                // Process child nodes
                if (!CollectionUtils.isEmpty(children)) {
                    buildEntity(entity, identitySourceId, children, createSet, updateSet, skipSet);
                }
            }
            // Build update data
            else {
                OrganizationEntity current = modal.getCurrent();
                // Whether modification is needed
                if (!equalsOrganization(modal, parent)) {
                    current.setName(thirdParty.getName());
                    current.setOrder(thirdParty.getOrder());
                    current.setParentId(parent.getId());
                    current.setPath(parent.getPath() + PATH_SEPARATOR + current.getId());
                    current.setDisplayPath(
                            parent.getDisplayPath() + PATH_SEPARATOR + thirdParty.getName());
                    current.setExternalId(thirdParty.getDeptId());
                    current.setLeaf(CollectionUtils.isEmpty(children));
                    current.setUpdateBy(SYSTEM_DEFAULT_USER_NAME);
                    current.setUpdateTime(LocalDateTime.now());
                    updateSet.add(current);
                } else {
                    skipSet.add(SkipOrganization.builder().organization(current)
                            .actionType(IdentitySourceActionType.UPDATE).reason("组织信息一致").build());
                }
                // Process child nodes
                if (!CollectionUtils.isEmpty(children)) {
                    buildEntity(current, identitySourceId, children, createSet, updateSet, skipSet);
                }
            }
        }
    }

    // Whether the organization needs updating
    private Boolean equalsOrganization(Modal modal, OrganizationEntity parent) {
        Dept thirdParty = modal.getThirdParty();
        OrganizationEntity current = modal.getCurrent();
        return
                // Name
                StringUtils.equals(thirdParty.getName(),current.getName()) &&
                // Parent department ID
                StringUtils.equals(parent.getId(),current.getParentId()) &&
                // Department path
                StringUtils.equals(parent.getPath() + PATH_SEPARATOR + current.getId(),current.getPath()) &&
                // Department display path
                StringUtils.equals(parent.getDisplayPath() + PATH_SEPARATOR + thirdParty.getName(),current.getDisplayPath()) &&
                // Leaf node
                Objects.equals(CollectionUtils.isEmpty(modal.getChildren()),current.getLeaf()) &&
                // Order
                Objects.equals(thirdParty.getOrder(),current.getOrder());
    }

    // Build model from sync data
    private Set<Modal> buildModal(List<Dept> deptList, List<OrganizationEntity> list) {
        if (Objects.isNull(deptList)) {
            return Collections.emptySet();
        }
        Set<Modal> modals = Sets.newLinkedHashSet();
        // KEY: external ID; VALUE: department entity
        Map<String, OrganizationEntity> map = new HashMap<>(16);
        list.forEach(dept -> map.put(dept.getExternalId(), dept));
        for (Dept dept : deptList) {
            if (map.containsKey(dept.getDeptId())) {
                Modal modal = new Modal();
                modal.setCurrent(map.get(dept.getDeptId()));
                modal.setThirdParty(dept);
                modal.setChildren(buildModal(dept.getChildren(), list));
                modals.add(modal);
                continue;
            }
            Modal modal = new Modal();
            modal.setThirdParty(dept);
            modal.setChildren(buildModal(dept.getChildren(), list));
            modals.add(modal);
        }
        return modals;
    }

    // Create root organization
    private OrganizationEntity createRootOrganization(String id,
                                                      IdentitySourceEntity identitySource,
                                                      Boolean isLeaf) {
        // Add node
        entityManager.createNativeQuery("INSERT INTO pudans_organization (id_, code_, name_, parent_id, is_leaf, external_id, data_origin, type_, is_enabled, path_, display_path, identity_source_id,create_by,update_by,is_deleted) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")
                .setParameter(1,id)
                .setParameter(2,RandomStringUtils.randomAlphanumeric(7))
                .setParameter(3,identitySource.getName())
                .setParameter(4,ROOT_DEPT_ID)
                .setParameter(5,isLeaf)
                .setParameter(6,getRootExternalId(identitySource))
                .setParameter(7,getDataOrigin(identitySource).getType())
                .setParameter(8, OrganizationType.DEPARTMENT.getCode())
                .setParameter(9,Boolean.TRUE)
                .setParameter(10,PATH_SEPARATOR + ROOT_DEPT_ID + PATH_SEPARATOR + id)
                .setParameter(11,PATH_SEPARATOR + ROOT_DEPT_NAME + PATH_SEPARATOR + identitySource.getName())
                .setParameter(12,identitySource.getId())
                .setParameter(13,SYSTEM_DEFAULT_USER_NAME)
                .setParameter(14,SYSTEM_DEFAULT_USER_NAME)
                .setParameter(15,false)
                .executeUpdate();
        return organizationRepository.findById(id).orElse(null);
    }

    // Create sync record
    private void saveSyncHistoryRecord(String historyId, Statistics statistics) {
        // Create organization
        saveCreateOrganizationsRecord(historyId, statistics.getCreateOrganizations());
        // Update organization record
        saveUpdateOrganizationsRecord(historyId, statistics.getUpdateOrganizations());
        // Delete organization record
        saveDeleteOrganizationsRecord(historyId, statistics.getDeleteOrganizations());
        // Skip organization record
        saveSkipOrganizationsRecord(historyId, statistics.getSkipOrganizations());
    }

    // Save created organization record
    private void saveCreateOrganizationsRecord(String historyId,
                                               Set<OrganizationEntity> createOrganizations) {
        List<IdentitySourceSyncRecordEntity> list = Lists.newArrayList();
        createOrganizations.forEach(user -> {
            IdentitySourceSyncRecordEntity entity = new IdentitySourceSyncRecordEntity();
            entity.setId(UUID.randomUUID().toString());
            entity.setSyncHistoryId(historyId);
            entity.setStatus(SyncStatus.SUCCESS);
            entity.setObjectType(IdentitySourceObjectType.ORGANIZATION);
            entity.setObjectId(user.getId());
            entity.setObjectName(user.getName());
            entity.setActionType(IdentitySourceActionType.INSERT);
            // Other information
            entity.setCreateBy(SYSTEM_DEFAULT_USER_NAME);
            entity.setCreateTime(LocalDateTime.now());
            entity.setUpdateBy(SYSTEM_DEFAULT_USER_NAME);
            entity.setUpdateTime(LocalDateTime.now());
            list.add(entity);
        });
        // Save
        identitySourceSyncRecordRepository.batchSave(list);
    }

    // Save updated organization record
    private void saveUpdateOrganizationsRecord(String historyId,
                                               Set<OrganizationEntity> updateOrganizations) {
        List<IdentitySourceSyncRecordEntity> list = Lists.newArrayList();
        updateOrganizations.forEach(user -> {
            IdentitySourceSyncRecordEntity entity = new IdentitySourceSyncRecordEntity();
            entity.setId(UUID.randomUUID().toString());
            entity.setSyncHistoryId(historyId);
            entity.setStatus(SyncStatus.SUCCESS);
            entity.setObjectType(IdentitySourceObjectType.ORGANIZATION);
            entity.setObjectId(user.getId());
            entity.setObjectName(user.getName());
            entity.setActionType(IdentitySourceActionType.UPDATE);
            // Other information
            entity.setCreateBy(SYSTEM_DEFAULT_USER_NAME);
            entity.setCreateTime(LocalDateTime.now());
            entity.setUpdateBy(SYSTEM_DEFAULT_USER_NAME);
            entity.setUpdateTime(LocalDateTime.now());
            list.add(entity);
        });
        // Save
        identitySourceSyncRecordRepository.batchSave(list);
    }

    // Save deleted organization record
    private void saveDeleteOrganizationsRecord(String historyId,
                                               Set<OrganizationEntity> deleteOrganizations) {
        List<IdentitySourceSyncRecordEntity> list = Lists.newArrayList();
        deleteOrganizations.forEach(user -> {
            IdentitySourceSyncRecordEntity entity = new IdentitySourceSyncRecordEntity();
            entity.setId(UUID.randomUUID().toString());
            entity.setSyncHistoryId(historyId);
            entity.setStatus(SyncStatus.SUCCESS);
            entity.setObjectType(IdentitySourceObjectType.ORGANIZATION);
            entity.setObjectId(user.getId());
            entity.setObjectName(user.getName());
            entity.setActionType(IdentitySourceActionType.DELETE);
            // Other information
            entity.setCreateBy(SYSTEM_DEFAULT_USER_NAME);
            entity.setCreateTime(LocalDateTime.now());
            entity.setUpdateBy(SYSTEM_DEFAULT_USER_NAME);
            entity.setUpdateTime(LocalDateTime.now());
            list.add(entity);
        });
        // Save
        identitySourceSyncRecordRepository.batchSave(list);
    }

    // Save skipped organization record
    private void saveSkipOrganizationsRecord(String historyId,
                                             Set<SkipOrganization> skipOrganizations) {
        List<IdentitySourceSyncRecordEntity> list = Lists.newArrayList();
        skipOrganizations.forEach(skipUser -> {
            OrganizationEntity organization = skipUser.getOrganization();
            IdentitySourceSyncRecordEntity entity = new IdentitySourceSyncRecordEntity();
            entity.setId(UUID.randomUUID().toString());
            entity.setSyncHistoryId(historyId);
            entity.setStatus(SyncStatus.SKIP);
            entity.setObjectType(IdentitySourceObjectType.ORGANIZATION);
            entity.setObjectId(organization.getId());
            entity.setObjectName(organization.getName());
            entity.setActionType(skipUser.getActionType());
            // Other information
            entity.setCreateBy(SYSTEM_DEFAULT_USER_NAME);
            entity.setCreateTime(LocalDateTime.now());
            entity.setUpdateBy(SYSTEM_DEFAULT_USER_NAME);
            entity.setUpdateTime(LocalDateTime.now());
            entity.setDesc(skipUser.getReason());
            list.add(entity);
        });
        // Save
        identitySourceSyncRecordRepository.batchSave(list);
    }

    @Data
    public static class Modal implements Serializable {
        // Master data (empty means new data)
        private OrganizationEntity current;
        // Third-party data
        private Dept thirdParty;
        // Subset
        private Set<Modal> children;

        public Boolean isNew() {
            return Objects.isNull(current);
        }
    }

    @Data
    public static class Statistics implements Serializable {

        @Serial
        private static final long serialVersionUID = 7025078516116020630L;

        // Create
        private Set<OrganizationEntity> createOrganizations;

        // Modify
        private Set<OrganizationEntity> updateOrganizations;

        // Delete
        private Set<OrganizationEntity> deleteOrganizations;

        // Skip
        private Set<SkipOrganization> skipOrganizations;

        public Statistics() {
            this.setUpdateOrganizations(Sets.newLinkedHashSet());
            this.setSkipOrganizations(Sets.newLinkedHashSet());
            this.setCreateOrganizations(Sets.newLinkedHashSet());
            this.setDeleteOrganizations(Sets.newLinkedHashSet());
        }
    }

    @Data
    @Builder
    private static class SkipOrganization {

        private OrganizationEntity organization;
        private IdentitySourceActionType actionType;
        private String reason;
    }
}
