package cn.tolim.pudans.iam.synchronizer.processor;

import cn.tolim.pudans.iam.communal.entity.account.OrganizationEntity;
import cn.tolim.pudans.iam.communal.entity.account.OrganizationMemberEntity;
import cn.tolim.pudans.iam.communal.entity.account.UserEntity;
import cn.tolim.pudans.iam.communal.entity.identitysource.IdentitySourceEntity;
import cn.tolim.pudans.iam.communal.entity.identitysource.IdentitySourceSyncHistoryEntity;
import cn.tolim.pudans.iam.communal.entity.identitysource.IdentitySourceSyncRecordEntity;
import cn.tolim.pudans.iam.communal.entity.identitysource.config.StrategyConfig;
import cn.tolim.pudans.iam.communal.enums.SyncStatus;
import cn.tolim.pudans.iam.communal.enums.TriggerType;
import cn.tolim.pudans.iam.communal.enums.identitysource.IdentitySourceActionType;
import cn.tolim.pudans.iam.communal.enums.identitysource.IdentitySourceObjectType;
import cn.tolim.pudans.iam.communal.repository.account.*;
import cn.tolim.pudans.iam.communal.repository.identitysource.IdentitySourceRepository;
import cn.tolim.pudans.iam.communal.repository.identitysource.IdentitySourceSyncHistoryRepository;
import cn.tolim.pudans.iam.communal.repository.identitysource.IdentitySourceSyncRecordRepository;
import cn.tolim.pudans.iam.communal.storage.Storage;
import cn.tolim.pudans.iam.core.message.mail.MailMsgEventPublish;
import cn.tolim.pudans.iam.identitysource.core.domain.User;
import cn.tolim.pudans.iam.identitysource.core.processor.IdentitySourceSyncUserPostProcessor;
import cn.tolim.pudans.iam.infra.repository.base.IdEntity;
import cn.tolim.pudans.iam.infra.security.password.PasswordGenerator;
import cn.tolim.pudans.iam.synchronizer.provider.IdentitySourceProviderResolver;
import com.alibaba.fastjson2.JSONObject;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import jakarta.persistence.EntityManager;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.util.CollectionUtils;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.tolim.pudans.iam.infra.constant.IamConstants.SYSTEM_DEFAULT_USER_NAME;
import static java.util.stream.Collectors.toSet;


@Slf4j
@Component
@SuppressWarnings({"AlibabaMethodTooLong", "DuplicatedCode"})
public class DefaultIdentitySourceUserPostProcessor extends AbstractIdentitySourcePostProcessor
        implements IdentitySourceSyncUserPostProcessor {

    // User repository
    private final UserRepository userRepository;
    // User detail repository
    private final UserDetailRepository userDetailRepository;
    // Organization repository
    private final OrganizationRepository organizationRepository;
    // Organization member repository
    private final OrganizationMemberRepository organizationMemberRepository;
    // Delete user group associations
    private final UserGroupMemberRepository userGroupMemberRepository;

    public DefaultIdentitySourceUserPostProcessor(MailMsgEventPublish mailMsgEventPublish,
                                                  TransactionDefinition transactionDefinition,
                                                  PlatformTransactionManager platformTransactionManager,
                                                  EntityManager entityManager,
                                                  IdentitySourceRepository identitySourceRepository,
                                                  IdentitySourceSyncHistoryRepository identitySourceSyncHistoryRepository,
                                                  IdentitySourceSyncRecordRepository identitySourceSyncRecordRepository,
                                                  PasswordEncoder passwordEncoder,
                                                  PasswordGenerator passwordGenerator,
                                                  UserRepository userRepository,
                                                  UserDetailRepository userDetailRepository,
                                                  OrganizationMemberRepository organizationMemberRepository,
                                                  UserGroupMemberRepository userGroupMemberRepository,
                                                  OrganizationRepository organizationRepository,
                                                  Storage storage,
                                                  IdentitySourceProviderResolver identitySourceProviderResolver) {
        super(mailMsgEventPublish, passwordEncoder, passwordGenerator, transactionDefinition,
                platformTransactionManager, entityManager, identitySourceRepository,
                identitySourceSyncHistoryRepository, identitySourceSyncRecordRepository, storage,
                identitySourceProviderResolver);
        this.userRepository = userRepository;
        this.userDetailRepository = userDetailRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.userGroupMemberRepository = userGroupMemberRepository;
        this.organizationRepository = organizationRepository;
    }

    @Override
    public void process(String batch, String identitySourceId, List<User> thirdPartyUserList,
                        LocalDateTime startTime, TriggerType triggerType) {
        ProcessDataResult processData = null;
        // Save sync history
        IdentitySourceSyncHistoryEntity history = saveSyncHistory(batch, identitySourceId,
                startTime, triggerType);
        // Start transaction manually
        TransactionStatus transactionStatus = platformTransactionManager
                .getTransaction(transactionDefinition);
        IdentitySourceEntity identitySource = getIdentitySource(identitySourceId);
        try {
            // Initialize data
            InitDataResult initData = initData(identitySource, thirdPartyUserList);
            // Process data
            processData = processData(identitySource, initData);
            // Validate data
            validateData(processData);
            // Batch save users
            if (!CollectionUtils.isEmpty(processData.getCreateUsers())) {
                List<UserEntity> createUserList = Lists.newArrayList(processData.getCreateUsers());
                userRepository.batchSave(createUserList);
            }
            // Batch update users
            if (!CollectionUtils.isEmpty(processData.getUpdateUsers())) {
                List<UserEntity> updateUserList = Lists.newArrayList(processData.getUpdateUsers());
                userRepository.batchUpdate(updateUserList);
            }
            // Save organization member relationships
            if (!CollectionUtils.isEmpty(processData.getCreateOrganizationMembers())) {
                List<OrganizationMemberEntity> organizationMemberEntityList = Lists
                        .newArrayList(processData.getCreateOrganizationMembers());
                organizationMemberRepository.batchSave(organizationMemberEntityList);
            }
            // Delete related data
            List<String> deleteUserIds;
            if (!CollectionUtils.isEmpty(processData.getDeleteUsers())) {
                deleteUserIds = processData.getDeleteUsers().stream().map(IdEntity::getId).toList();
                // Delete user
                userRepository.deleteAllById(deleteUserIds);
                // Delete user details
                userDetailRepository.deleteAllByUserIdIn(deleteUserIds);
                // Delete organization-user associations
                organizationMemberRepository.deleteAllByUserIdIn(deleteUserIds);
                // Delete user group relationships
                userGroupMemberRepository.deleteAllByUserIdIn(deleteUserIds);
            }
            // Delete organization relationship
            if (!CollectionUtils.isEmpty(processData.getDeleteOrganizationMembers())) {
                for (String userId : processData.getDeleteOrganizationMembers().keySet()) {
                    for (String orgId : processData.getDeleteOrganizationMembers().get(userId)) {
                        organizationMemberRepository.deleteByOrgIdAndUserId(orgId, userId);
                    }
                }
            }
            // Update sync history
            updateSyncHistory(processData, history, SyncStatus.SUCCESS);
            // Create sync record
            saveSyncHistoryRecord(history.getId(), processData);
            // Commit transaction
            platformTransactionManager.commit(transactionStatus);
            if (!CollectionUtils.isEmpty(processData.getCreateUsers()) && Boolean.TRUE
                    .equals(identitySource.getStrategyConfig().getUser().getEmailNotify())) {
                // Send password notification
                publishMessage(Lists.newArrayList(processData.getCreateUsers()));
            }
        } catch (Exception e) {
            log.error("处理用户数据发生异常", e);
            platformTransactionManager.rollback(transactionStatus);
            updateSyncHistory(processData, history, SyncStatus.FAIL);
        }
    }

    // Create sync record
    private void saveSyncHistoryRecord(String historyId, ProcessDataResult processData) {
        // Create user
        saveCreateUsersRecord(historyId, processData.getCreateUsers());
        // Update user record
        saveUpdateUsersRecord(historyId, processData.getUpdateUsers());
        // Delete user record
        saveDeleteUsersRecord(historyId, processData.getDeleteUsers());
        // Skip user record
        saveSkipUsersRecord(historyId, processData.getSkipUsers());
    }

    // Save created user record
    private void saveCreateUsersRecord(String historyId, Set<UserEntity> createUsers) {
        List<IdentitySourceSyncRecordEntity> list = Lists.newArrayList();
        createUsers.forEach(user -> {
            IdentitySourceSyncRecordEntity entity = new IdentitySourceSyncRecordEntity();
            entity.setId(UUID.randomUUID().toString());
            entity.setSyncHistoryId(historyId);
            entity.setStatus(SyncStatus.SUCCESS);
            entity.setObjectType(IdentitySourceObjectType.USER);
            entity.setObjectId(user.getId());
            entity.setObjectName(user.getFullName());
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

    // Save updated user record
    private void saveUpdateUsersRecord(String historyId, Set<UserEntity> updateUsers) {
        List<IdentitySourceSyncRecordEntity> list = Lists.newArrayList();
        updateUsers.forEach(user -> {
            IdentitySourceSyncRecordEntity entity = new IdentitySourceSyncRecordEntity();
            entity.setId(UUID.randomUUID().toString());
            entity.setSyncHistoryId(historyId);
            entity.setStatus(SyncStatus.SUCCESS);
            entity.setObjectType(IdentitySourceObjectType.USER);
            entity.setObjectId(user.getId());
            entity.setObjectName(user.getFullName());
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

    // Save deleted user record
    private void saveDeleteUsersRecord(String historyId, Set<UserEntity> deleteUsers) {
        List<IdentitySourceSyncRecordEntity> list = Lists.newArrayList();
        deleteUsers.forEach(user -> {
            IdentitySourceSyncRecordEntity entity = new IdentitySourceSyncRecordEntity();
            entity.setId(UUID.randomUUID().toString());
            entity.setSyncHistoryId(historyId);
            entity.setStatus(SyncStatus.SUCCESS);
            entity.setObjectType(IdentitySourceObjectType.USER);
            entity.setObjectId(user.getId());
            entity.setObjectName(user.getFullName());
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

    // Save skipped user record
    private void saveSkipUsersRecord(String historyId, Set<SkipUser> skipUsers) {
        List<IdentitySourceSyncRecordEntity> list = Lists.newArrayList();
        skipUsers.forEach(skipUser -> {
            UserEntity user = skipUser.getUser();
            IdentitySourceSyncRecordEntity entity = new IdentitySourceSyncRecordEntity();
            entity.setId(UUID.randomUUID().toString());
            entity.setSyncHistoryId(historyId);
            entity.setStatus(skipUser.getStatus());
            entity.setObjectType(IdentitySourceObjectType.USER);
            entity.setObjectId(user.getId());
            entity.setObjectName(user.getFullName());
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

    // Update sync history
    private void updateSyncHistory(ProcessDataResult processData,
                                   IdentitySourceSyncHistoryEntity history, SyncStatus status) {
        if (!Objects.isNull(processData)) {
            history.setSkippedCount(processData.getSkipUsers().size());
            history.setDeletedCount(processData.getDeleteUsers().size());
            history.setUpdatedCount(processData.getUpdateUsers().size());
            history.setCreatedCount(processData.getCreateUsers().size());
        }
        history.setEndTime(LocalDateTime.now());
        history.setStatus(status);
        identitySourceSyncHistoryRepository.save(history);
    }

    // Save sync history
    @NotNull
    private IdentitySourceSyncHistoryEntity saveSyncHistory(String batch, String identitySourceId,
                                                            LocalDateTime startTime,
                                                            TriggerType triggerType) {
        IdentitySourceSyncHistoryEntity history = new IdentitySourceSyncHistoryEntity();
        history.setBatch(batch);
        history.setIdentitySourceId(identitySourceId);
        history.setStartTime(startTime);
        history.setObjectType(IdentitySourceObjectType.USER);
        history.setTriggerType(triggerType);
        history.setStatus(SyncStatus.PENDING);
        identitySourceSyncHistoryRepository.save(history);
        return history;
    }

    // Initialize data
    private InitDataResult initData(IdentitySourceEntity identitySource,
                                    List<User> thirdPartyUserList) {
        StrategyConfig strategyConfig = identitySource.getStrategyConfig();
        String targetId = strategyConfig.getOrganization().getTargetId();
        // Wrap third-party organizations and users with the latest upstream structure: key=user, value=department list
        Map<User, Set<OrganizationEntity>> thirdPartyUserOrganizations = Maps.newHashMap();
        // Wrap current system organizations and users: key=user, value=department list
        Map<UserEntity, Set<OrganizationEntity>> currentUserOrganizations = Maps.newHashMap();
        // Query identity source organizations
        List<OrganizationEntity> identitySourceOrgList = organizationRepository.findByIdentitySourceId(identitySource.getId());
        Optional<OrganizationEntity> identitySourceRootOrg = organizationRepository.findById(targetId);
        // Add root organization
        identitySourceRootOrg.ifPresent(identitySourceOrgList::add);
        // Wrap third-party users
        for (User thirdPartyUser : thirdPartyUserList) {
            List<String> deptIdList = thirdPartyUser.getDeptIdList();
            List<OrganizationEntity> list = identitySourceOrgList.stream().filter(dept -> {
                String rootExternalId = getRootExternalId(identitySource);
                if (deptIdList.contains(rootExternalId) && targetId.equals(dept.getId())) {
                    return true;
                }
                return deptIdList.contains(dept.getExternalId());
            }).toList();
            thirdPartyUserOrganizations.put(thirdPartyUser, Sets.newHashSet(list));
        }

        // Wrap current users
        List<UserEntity> users = userRepository.findAllByIdentitySourceId(identitySource.getId());
        Map<String,OrganizationEntity> identitySourceOrgMap = identitySourceOrgList.stream().collect(Collectors.toMap(OrganizationEntity::getId, Function.identity()));
        users.forEach(user -> {
            List<OrganizationMemberEntity> organizationMembers = organizationMemberRepository.findAllByUserId(user.getId());
            List<String> orgIdList = organizationMembers.stream().map(OrganizationMemberEntity::getOrgId).toList();
            orgIdList.forEach(orgId -> {
                OrganizationEntity organization = identitySourceOrgMap.get(orgId);
                if (CollectionUtils.isEmpty(currentUserOrganizations.get(user))) {
                    currentUserOrganizations.put(user, Sets.newHashSet(organization));
                } else {
                    Set<OrganizationEntity> set = currentUserOrganizations.get(user);
                    set.add(organization);
                    currentUserOrganizations.put(user, set);
                }
            });
        });
        // Query organizations under the same source that have no associated users
        List<UserEntity> userNotExistOrgList = userRepository.findAllByOrgIdNotExistAndIdentitySourceId(identitySource.getId());
        userNotExistOrgList.forEach(user -> currentUserOrganizations.put(user,Sets.newHashSet()));
        return InitDataResult.builder().currentUsers(currentUserOrganizations).thirdPartyUsers(thirdPartyUserOrganizations)
                .build();
    }

    // Process data
    private ProcessDataResult processData(IdentitySourceEntity identitySource,
                                          InitDataResult initData) {
        // Create, modify, and other user sets: key=organization ID, value=user information
        Set<UserEntity> createUsers = new TreeSet<>(Comparator.comparing(UserEntity::getUsername)),
            updateUsers = new TreeSet<>(Comparator.comparing(UserEntity::getUsername));
        // Skipped user set: key=user, value=skip reason
        Set<SkipUser> skipUsers = Sets.newLinkedHashSet();
        // Current system users mapped to system departments
        Map<UserEntity, Set<OrganizationEntity>> currentUsers = initData.getCurrentUsers();
        // Upstream users mapped to upstream departments
        Map<User, Set<OrganizationEntity>> thirdPartyUsers = initData.getThirdPartyUsers();
        // Organization member relationships to create
        Set<OrganizationMemberEntity> createOrganizationMembers = Sets.newLinkedHashSet();
        // Organization member relationships to delete, KEY: user ID, VALUE: organization ID
        Map< String,Set<String>> deleteOrganizationMembers = Maps.newHashMap();
        // Upstream user exists in the current system
        currentUsers.keySet().stream().filter(i -> thirdPartyUsers.keySet().stream().map(User::getUserId).collect(toSet()).contains(i.getExternalId())).collect(toSet())
                .forEach(currentUser -> thirdPartyUsers.keySet().forEach(thirdPartyUser -> {
            if (currentUser.getExternalId().equals(thirdPartyUser.getUserId())) {
                log.info("处理上游用户:[{}]开始", thirdPartyUser.getUserId());
                // Process user information
                if (!equalsUser(thirdPartyUser, currentUser,identitySource.getId())) {
                    // Set updated user data
                    setUpdateUser(thirdPartyUser, currentUser,identitySource);
                    log.info("上游用户:[{}]对应系统用户:[{}]({})存在,用户信息不一致,修改用户信息：{}", thirdPartyUser.getUserId(), currentUser.getUsername(), currentUser.getId(), JSONObject.toJSONString(currentUser));
                    updateUsers.add(currentUser);
                } else {
                    skipUsers.add(SkipUser.builder().user(currentUser).actionType(IdentitySourceActionType.UPDATE).status(SyncStatus.SKIP).reason( "用户信息一致").build());
                    log.info("上游用户:[{}]对应系统用户:[{}]({})存在,用户信息一致", thirdPartyUser.getUserId(), currentUser.getUsername(), currentUser.getId());
                }
                // Process organization relationships
                Set<OrganizationEntity> thirdPartyOrganizations = thirdPartyUsers.get(thirdPartyUser);
                Set<OrganizationEntity> currentOrganizations = currentUsers.get(currentUser);
                // New relationships to create
                thirdPartyOrganizations.stream().filter(item -> !currentOrganizations.contains(item)).collect(toSet()).forEach(organization -> {
                    createOrganizationMember(createOrganizationMembers, currentUser, organization);
                    log.info("上游用户:[{}]对应当前系统用户:[{}]({})存在,需要创建组织[{}]({})与成员[{}]({})关系", thirdPartyUser.getUserId(), currentUser.getUsername(), currentUser.getId(), organization.getName(), organization.getId(), currentUser.getUsername(), currentUser.getId());
                });
                // Old relationships to delete
                currentOrganizations.stream().filter(item -> Objects.nonNull(item) && !thirdPartyOrganizations.contains(item)).collect(toSet()).forEach(organization -> {
                    Set<String> ids = deleteOrganizationMembers.get(currentUser.getId());
                    log.info("上游用户:[{}]对应当前系统用户:[{}]({})存在,需要删除组织[{}]({})与成员[{}]({})关系", thirdPartyUser.getUserId(), currentUser.getUsername(), currentUser.getId(), organization.getName(), organization.getId(), currentUser.getUsername(), currentUser.getId());
                    if (org.apache.commons.collections4.CollectionUtils.isNotEmpty(ids)) {
                        ids.add(organization.getId());
                        deleteOrganizationMembers.put(currentUser.getId(), ids);
                        return;
                    }
                    deleteOrganizationMembers.put(currentUser.getId(), Sets.newHashSet(organization.getId()));
                });
                log.info("处理上游用户:[{}]结束", thirdPartyUser.getUserId());
            }
        }));
        // Upstream user does not exist in the current system
        thirdPartyUsers.keySet().stream().filter(i -> !currentUsers.keySet().stream().map(UserEntity::getExternalId).collect(toSet()).contains(i.getUserId())).collect(toSet())
                .forEach(thirdPartyUser -> {
            log.info("处理上游用户:[{}]开始", thirdPartyUser.getUserId());
            // Add user information
            UserEntity userEntity = thirdPartyUserToUserEntity(thirdPartyUser, identitySource);
            log.info("上游用户:[{}]在当前系统不存在,创建用户: {}", thirdPartyUser.getUserId(), JSONObject.toJSONString(userEntity));
            createUsers.add(userEntity);
            // Process organization relationships
            thirdPartyUsers.get(thirdPartyUser).forEach(organization -> {
                createOrganizationMember(createOrganizationMembers, userEntity, organization);
                log.info("上游用户:[{}]在当前系统不存在,创建组织[{}]({})与成员[{}]({})关系", thirdPartyUser.getUserId(), organization.getName(), organization.getId(), userEntity.getUsername(), userEntity.getId());
            });
            log.info("处理上游用户:[{}]结束", thirdPartyUser.getUserId());
        });

        // Filter users to delete
        Set<UserEntity> deleteUsers = currentUsers.keySet().stream().filter(currentUser -> !thirdPartyUsers.keySet().stream().map(User::getUserId).collect(toSet()).contains(currentUser.getExternalId())).collect(toSet());
        for (UserEntity user : deleteUsers) {
            log.info("上游用户不存在,本地存在用户:[{}]({})", user.getUsername(),user.getId());
        }
        return ProcessDataResult.builder()
                // Users to create
                .createUsers(createUsers)
                // Users to update
                .updateUsers(updateUsers)
                // Users to skip
                .skipUsers(skipUsers)
                // User IDs to delete
                .deleteUsers(deleteUsers)
                // Organization member relationships to create
                .createOrganizationMembers(createOrganizationMembers)
                // Organization member relationships to delete
                .deleteOrganizationMembers(deleteOrganizationMembers).build();
    }

    // Validate data
    private void validateData(ProcessDataResult processData) {
        // Process user creation
        validateCreateUserData(processData);
        // Process user modification
        validateUpdateUserData(processData);
    }

    // Validate create user data
    private void validateCreateUserData(ProcessDataResult processData) {
        Set<UserEntity> createUsers = processData.getCreateUsers();
        Set<SkipUser> skipUsers = processData.getSkipUsers();
        Set<OrganizationMemberEntity> createOrganizationMembers = processData
                .getCreateOrganizationMembers();
        if (!CollectionUtils.isEmpty(createUsers)) {
            // Query by username
            List<UserEntity> existUsernames = getByUsernames(createUsers);
            // Query by email
            List<UserEntity> existEmails = getEmails(createUsers);
            // Query by phone number
            List<UserEntity> existPhones = getPhones(createUsers);
            existUsernames.forEach(user -> {
                Iterator<UserEntity> userIterator = createUsers.iterator();
                while (userIterator.hasNext()) {
                    UserEntity createUser = userIterator.next();
                    // Validate username
                    if (createUser.getUsername().equals(user.getUsername())) {
                        log.info("跳过上游用户:[{{}}]创建,用户名:[{}]在系统已存在", createUser.getExternalId(),
                                user.getUsername());
                        skipUsers.add(SkipUser.builder().user(createUser)
                                .actionType(IdentitySourceActionType.INSERT).status(SyncStatus.FAIL)
                                .reason("用户名 [" + createUser.getUsername() + "] 已存在").build());
                        userIterator.remove();
                        // Remove creation relationship
                        createOrganizationMembers
                                .removeIf(member -> member.getUserId().equals(createUser.getId()));
                    }
                }
            });
            existEmails.forEach(user -> {
                Iterator<UserEntity> userIterator = createUsers.iterator();
                while (userIterator.hasNext()) {
                    UserEntity createUser = userIterator.next();
                    // Validate email
                    if (StringUtils.isNotBlank(createUser.getEmail())
                            && createUser.getEmail().equals(user.getEmail())) {
                        log.info("跳过上游用户:[{{}}]创建,邮箱:[{}]在系统已存在", createUser.getExternalId(),
                                user.getEmail());
                        skipUsers.add(SkipUser.builder().user(createUser)
                                .actionType(IdentitySourceActionType.INSERT).status(SyncStatus.FAIL)
                                .reason("邮箱 [" + createUser.getEmail() + "] 已存在").build());
                        userIterator.remove();
                        // Remove creation relationship
                        createOrganizationMembers
                                .removeIf(member -> member.getUserId().equals(createUser.getId()));
                    }
                }
            });
            existPhones.forEach(user -> {
                Iterator<UserEntity> userIterator = createUsers.iterator();
                while (userIterator.hasNext()) {
                    UserEntity createUser = userIterator.next();
                    // Validate phone number
                    if (StringUtils.isNotBlank(createUser.getPhone())
                            && createUser.getPhone().equals(user.getPhone())) {
                        log.info("跳过上游用户:[{{}}]创建,手机号:[{}]在系统已存在", createUser.getExternalId(),
                                user.getPhone());
                        skipUsers.add(SkipUser.builder().user(createUser)
                                .actionType(IdentitySourceActionType.INSERT).status(SyncStatus.FAIL)
                                .reason("手机号 [" + createUser.getPhone() + "] 已存在").build());
                        userIterator.remove();
                        // Remove creation relationship
                        createOrganizationMembers
                                .removeIf(member -> member.getUserId().equals(createUser.getId()));
                    }
                }
            });
        }
    }

    // Validate modify user data
    private void validateUpdateUserData(ProcessDataResult processData) {
        Set<UserEntity> updateUsers = processData.getUpdateUsers();
        Set<SkipUser> skipUsers = processData.getSkipUsers();
        if (!CollectionUtils.isEmpty(updateUsers)) {
            // Query by username
            List<UserEntity> existUsernames = getByUsernames(updateUsers);
            // Query by email
            List<UserEntity> existEmails = getEmails(updateUsers);
            // Query by phone number
            List<UserEntity> existPhones = getPhones(updateUsers);
            existUsernames.forEach(user -> {
                Iterator<UserEntity> userIterator = updateUsers.iterator();
                while (userIterator.hasNext()) {
                    UserEntity updateUser = userIterator.next();
                    // Validate username
                    if (updateUser.getUsername().equals(user.getUsername())
                            && !updateUser.getId().equals(user.getId())) {
                        log.info("跳过上游用户:[{{}}]修改,用户名:[{}]在系统已存在", updateUser.getExternalId(),
                                user.getUsername());
                        skipUsers.add(SkipUser.builder().user(updateUser)
                                .actionType(IdentitySourceActionType.UPDATE).status(SyncStatus.FAIL)
                                .reason("用户名 [" + updateUser.getUsername() + "] 已存在").build());
                        userIterator.remove();
                    }
                }
            });
            existEmails.forEach(user -> {
                Iterator<UserEntity> userIterator = updateUsers.iterator();
                while (userIterator.hasNext()) {
                    UserEntity updateUser = userIterator.next();
                    // Validate email
                    if (StringUtils.isNotBlank(updateUser.getEmail())
                            && updateUser.getEmail().equals(user.getEmail())
                            && !updateUser.getId().equals(user.getId())) {
                        log.info("跳过上游用户:[{{}}]修改,邮箱:[{}]在系统已存在", updateUser.getExternalId(),
                                user.getEmail());
                        skipUsers.add(SkipUser.builder().user(updateUser)
                                .actionType(IdentitySourceActionType.UPDATE).status(SyncStatus.FAIL)
                                .reason("邮箱 [" + updateUser.getEmail() + "] 已存在").build());
                        userIterator.remove();
                    }
                }
            });
            existPhones.forEach(user -> {
                Iterator<UserEntity> userIterator = updateUsers.iterator();
                while (userIterator.hasNext()) {
                    UserEntity updateUser = userIterator.next();
                    // Validate phone number
                    if (StringUtils.isNotBlank(updateUser.getPhone())
                            && updateUser.getPhone().equals(user.getPhone())
                            && !updateUser.getId().equals(user.getId())) {
                        log.info("跳过上游用户:[{{}}]修改,手机号:[{}]在系统已存在", updateUser.getExternalId(),
                                user.getPhone());
                        skipUsers.add(SkipUser.builder().user(updateUser)
                                .actionType(IdentitySourceActionType.UPDATE).status(SyncStatus.FAIL)
                                .reason("手机号 [" + updateUser.getPhone() + "] 已存在").build());
                        userIterator.remove();
                    }
                }
            });
        }
    }

    private List<UserEntity> getPhones(Set<UserEntity> list) {
        List<String> phones = list.stream().map(UserEntity::getPhone).toList();
        return userRepository.findAllByPhoneIn(phones);
    }

    private List<UserEntity> getEmails(Set<UserEntity> userList) {
        List<String> emails = userList.stream().map(UserEntity::getEmail).toList();
        return userRepository.findAllByEmailIn(emails);
    }

    private List<UserEntity> getByUsernames(Set<UserEntity> userList) {
        List<String> usernames = userList.stream().map(UserEntity::getUsername).toList();
        return userRepository.findAllByUsernameIn(usernames);
    }

    @Data
    @Builder
    private static class InitDataResult implements Serializable {

        @Serial
        private static final long serialVersionUID = 5558133274263199406L;

        // Wrap third-party departments and users with the latest upstream structure: key=user, value=current department
        private Map<User, Set<OrganizationEntity>> thirdPartyUsers;
        // Wrap current system departments and users: key=user, value=all user departments
        private Map<UserEntity, Set<OrganizationEntity>> currentUsers;
    }

    @Data
    @Builder
    private static class ProcessDataResult implements Serializable {

        @Serial
        private static final long serialVersionUID = 5995750190105246430L;

        // Create user set
        private Set<UserEntity> createUsers;

        // Update user set
        private Set<UserEntity> updateUsers;

        // Skip user set
        private Set<SkipUser> skipUsers;

        // Delete user
        private Set<UserEntity> deleteUsers;

        // Create organization member relationships
        private Set<OrganizationMemberEntity> createOrganizationMembers;

        // Delete organization member relationships, KEY: user ID, VALUE: organization IDs
        private Map<String, Set<String>> deleteOrganizationMembers;
    }
}
