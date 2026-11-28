package cn.tolim.pudans.iam.synchronizer.processor;

import cn.tolim.pudans.iam.communal.entity.account.OrganizationEntity;
import cn.tolim.pudans.iam.communal.entity.account.OrganizationMemberEntity;
import cn.tolim.pudans.iam.communal.entity.account.UserEntity;
import cn.tolim.pudans.iam.communal.entity.identitysource.IdentitySourceEntity;
import cn.tolim.pudans.iam.communal.entity.identitysource.IdentitySourceEventRecordEntity;
import cn.tolim.pudans.iam.communal.enums.SyncStatus;
import cn.tolim.pudans.iam.communal.enums.account.OrganizationType;
import cn.tolim.pudans.iam.communal.enums.identitysource.IdentitySourceActionType;
import cn.tolim.pudans.iam.communal.enums.identitysource.IdentitySourceObjectType;
import cn.tolim.pudans.iam.communal.repository.account.*;
import cn.tolim.pudans.iam.communal.repository.identitysource.IdentitySourceEventRecordRepository;
import cn.tolim.pudans.iam.communal.repository.identitysource.IdentitySourceRepository;
import cn.tolim.pudans.iam.communal.repository.identitysource.IdentitySourceSyncHistoryRepository;
import cn.tolim.pudans.iam.communal.repository.identitysource.IdentitySourceSyncRecordRepository;
import cn.tolim.pudans.iam.communal.storage.Storage;
import cn.tolim.pudans.iam.core.message.mail.MailMsgEventPublish;
import cn.tolim.pudans.iam.identitysource.core.domain.Dept;
import cn.tolim.pudans.iam.identitysource.core.domain.User;
import cn.tolim.pudans.iam.identitysource.core.enums.IdentitySourceEventReceiveType;
import cn.tolim.pudans.iam.identitysource.core.processor.IdentitySourceEventPostProcessor;
import cn.tolim.pudans.iam.identitysource.core.processor.modal.IdentitySourceEventProcessData;
import cn.tolim.pudans.iam.infra.exception.PudansException;
import cn.tolim.pudans.iam.infra.repository.base.IdEntity;
import cn.tolim.pudans.iam.infra.security.password.PasswordGenerator;
import cn.tolim.pudans.iam.synchronizer.provider.IdentitySourceProviderResolver;
import com.alibaba.fastjson2.JSONObject;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StopWatch;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.tolim.pudans.iam.infra.constant.IamConstants.PATH_SEPARATOR;
import static cn.tolim.pudans.iam.infra.constant.IamConstants.SYSTEM_DEFAULT_USER_NAME;
import static java.util.stream.Collectors.toSet;


@Slf4j
@Component
@SuppressWarnings({"rawtypes", "unchecked", "DuplicatedCode"})
public class DefaultIdentitySourceEventPostProcessor extends AbstractIdentitySourcePostProcessor
        implements IdentitySourceEventPostProcessor {

    // UserRepository
    private final UserRepository userRepository;
    // OrganizationRepository
    private final OrganizationRepository organizationRepository;
    // OrganizationMemberRepository
    private final OrganizationMemberRepository organizationMemberRepository;
    // IdentitySourceEventRecordRepository
    private final IdentitySourceEventRecordRepository identitySourceEventRecordRepository;
    // UserDetailRepository
    private final UserDetailRepository userDetailRepository;
    // UserGroupMemberRepository
    private final UserGroupMemberRepository userGroupMemberRepository;

    public DefaultIdentitySourceEventPostProcessor(MailMsgEventPublish mailMsgEventPublish,
                                                   PasswordEncoder passwordEncoder,
                                                   PasswordGenerator passwordGenerator,
                                                   TransactionDefinition transactionDefinition,
                                                   PlatformTransactionManager platformTransactionManager,
                                                   EntityManager entityManager,
                                                   IdentitySourceRepository identitySourceRepository,
                                                   IdentitySourceSyncHistoryRepository identitySourceSyncHistoryRepository,
                                                   IdentitySourceSyncRecordRepository identitySourceSyncRecordRepository,
                                                   UserRepository userRepository,
                                                   OrganizationRepository organizationRepository,
                                                   OrganizationMemberRepository organizationMemberRepository,
                                                   IdentitySourceEventRecordRepository identitySourceEventRecordRepository,
                                                   Storage storage,
                                                   UserDetailRepository userDetailRepository,
                                                   UserGroupMemberRepository userGroupMemberRepository,
                                                   IdentitySourceProviderResolver identitySourceProviderResolver) {
        super(mailMsgEventPublish, passwordEncoder, passwordGenerator, transactionDefinition,
                platformTransactionManager, entityManager, identitySourceRepository,
                identitySourceSyncHistoryRepository, identitySourceSyncRecordRepository, storage,
                identitySourceProviderResolver);
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.identitySourceEventRecordRepository = identitySourceEventRecordRepository;
        this.userDetailRepository = userDetailRepository;
        this.userGroupMemberRepository = userGroupMemberRepository;
    }

    private static void addRecord(LocalDateTime eventTime, IdentitySourceEntity identitySource,
                                  String id, String name,
                                  List<IdentitySourceEventRecordEntity> records, SyncStatus status,
                                  IdentitySourceObjectType objectType,
                                  IdentitySourceActionType actionType, String desc) {
        // Log
        IdentitySourceEventRecordEntity record = new IdentitySourceEventRecordEntity();
        record.setObjectId(id);
        record.setIdentitySourceId(identitySource.getId());
        record.setObjectName(name);
        record.setObjectType(objectType);
        record.setActionType(actionType);
        record.setEventTime(eventTime);
        record.setStatus(status);
        // Additional information
        record.setId(UUID.randomUUID().toString());
        record.setCreateBy(SYSTEM_DEFAULT_USER_NAME);
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateBy(SYSTEM_DEFAULT_USER_NAME);
        record.setUpdateTime(LocalDateTime.now());
        record.setDesc(desc);
        records.add(record);
    }

    // Process data
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void process(IdentitySourceEventProcessData eventData) {
        log.info("处理数据上游回调数据开始: {}", eventData);
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        IdentitySourceEventReceiveType eventType = eventData.getEventType();
        IdentitySourceEntity identitySource = getIdentitySource(eventData.getId());
        LocalDateTime eventTime = eventData.getEventTime();
        // User added
        if (IdentitySourceEventReceiveType.USER_ADD.equals(eventType)) {
            createUsers(eventData.getData(), eventTime, identitySource);
            // User modified
        } else if (IdentitySourceEventReceiveType.USER_MODIFY.equals(eventType)) {
            modifyUsers(eventData.getData(), eventTime, identitySource);
            // User departed
        } else if (IdentitySourceEventReceiveType.USER_LEAVE.equals(eventType)) {
            leaveUsers(eventData.getData(), eventTime, identitySource);
            // Department added
        } else if (IdentitySourceEventReceiveType.DEPT_CREATE.equals(eventType)) {
            createOrganizations(eventData.getData(), eventTime, identitySource);
            // Department modified
        } else if (IdentitySourceEventReceiveType.DEPT_MODIFY.equals(eventType)) {
            modifyOrganizations(eventData.getData(), eventTime, identitySource);
            // Department removed
        } else if (IdentitySourceEventReceiveType.DEPT_REMOVE.equals(eventType)) {
            removeOrganizations(eventData.getData(), eventTime, identitySource);
        } else {
            throw new PudansException(eventData.getProvider().getName() + "身份提供商事件回调非法事件");
        }
        stopWatch.stop();
        log.info("处理数据上游回调数据结束, 执行时长: {} ms", stopWatch.getTotalTimeMillis());
    }

    // Create user
    void createUsers(List<User> thirdPartyUserList, LocalDateTime eventTime,
                     IdentitySourceEntity identitySource) {
        List<IdentitySourceEventRecordEntity> records = new ArrayList<>();
        List<UserEntity> createUsers = new ArrayList<>();
        Set<OrganizationMemberEntity> createOrganizationMembers = Sets.newHashSet();
        String targetId = identitySource.getStrategyConfig().getOrganization().getTargetId();
        // Query by username
        List<UserEntity> existUsernames = getByUsernames(
                thirdPartyUserList.stream().map(User::getUserId).toList());
        // Query by email
        List<UserEntity> existEmails = getEmails(
                thirdPartyUserList.stream().map(User::getEmail).toList());
        // Query by phone number
        List<UserEntity> existPhones = getPhones(
                thirdPartyUserList.stream().map(User::getPhone).toList());
        thirdPartyUserList.forEach(thirdPartyUser -> {
            log.info("处理上游用户新增事件:[{}]开始", thirdPartyUser.getUserId());
            UserEntity createUser = thirdPartyUserToUserEntity(thirdPartyUser, identitySource);
            log.info("根据上游用户:[{}],创建本地用户: {}", thirdPartyUser.getUserId(),
                    JSONObject.toJSONString(createUser));
            if (existUsernames.stream()
                    .anyMatch(user -> createUser.getUsername().equals(user.getUsername()))) {
                log.info("跳过上游用户:[{{}}]创建,用户名:[{}]在系统已存在", createUser.getExternalId(),
                        createUser.getUsername());
                addRecord(eventTime, identitySource, createUser.getId(), createUser.getFullName(),
                        records, SyncStatus.FAIL, IdentitySourceObjectType.USER,
                        IdentitySourceActionType.INSERT, "用户名 [" + createUser.getUsername() + "] 已存在");
                return;
            }
            if (StringUtils.isNotBlank(createUser.getEmail()) && existEmails.stream()
                    .anyMatch(user -> createUser.getEmail().equals(user.getEmail()))) {
                log.info("跳过上游用户:[{{}}]创建,邮箱:[{}]在系统已存在", createUser.getExternalId(),
                        createUser.getEmail());
                addRecord(eventTime, identitySource, createUser.getId(), createUser.getFullName(),
                        records, SyncStatus.FAIL, IdentitySourceObjectType.USER,
                        IdentitySourceActionType.INSERT, "邮箱 [" + createUser.getEmail() + "] 已存在");
                return;
            }
            if (StringUtils.isNotBlank(createUser.getPhone()) && existPhones.stream()
                    .anyMatch(user -> createUser.getPhone().equals(user.getPhone()))) {
                log.info("跳过上游用户:[{{}}]创建,手机号:[{}]在系统已存在", createUser.getExternalId(),
                        createUser.getPhone());
                addRecord(eventTime, identitySource, createUser.getId(), createUser.getFullName(),
                        records, SyncStatus.FAIL, IdentitySourceObjectType.USER,
                        IdentitySourceActionType.INSERT, "手机号 [" + createUser.getPhone() + "] 已存在");
                return;
            }
            createUsers.add(createUser);
            // Organization relationship
            List<OrganizationEntity> organizationList = organizationRepository
                    .findByIdentitySourceIdAndExternalIdIn(identitySource.getId(),
                            thirdPartyUser.getDeptIdList());
            // Root organization
            if (thirdPartyUser.getDeptIdList().stream().anyMatch(
                    deptId -> deptId.equals(getRootExternalId(identitySource)))) {
                organizationRepository.findById(targetId).ifPresent(organizationList::add);
            }
            organizationList.forEach(organization -> {
                log.info("根据上游用户:[{}],创建组织[{}]({})与成员[{}]({})关系", thirdPartyUser.getUserId(),
                        organization.getName(), organization.getId(), createUser.getUsername(),
                        createUser.getId());
                createOrganizationMember(createOrganizationMembers, createUser, organization);
            });
            addRecord(eventTime, identitySource, createUser.getId(), createUser.getFullName(),
                    records, SyncStatus.SUCCESS, IdentitySourceObjectType.USER,
                    IdentitySourceActionType.INSERT, "新增成功");
            log.info("处理上游用户新增事件:[{}]结束", thirdPartyUser.getUserId());
        });
        // Save user
        userRepository.batchSave(createUsers);
        // Save organization relationship
        organizationMemberRepository.batchSave(Lists.newArrayList(createOrganizationMembers));
        // Save event record
        identitySourceEventRecordRepository.batchSave(records);
        // Batch send welcome SMS/email (password notification)
        if (Boolean.TRUE.equals(identitySource.getStrategyConfig().getUser().getEmailNotify())) {
            publishMessage(createUsers);
        }
    }

    // Modify user
    void modifyUsers(List<User> thirdPartyUserList, LocalDateTime eventTime,
                     IdentitySourceEntity identitySource) {
        List<IdentitySourceEventRecordEntity> records = new ArrayList<>();
        List<UserEntity> updateUsers = new ArrayList<>();
        Set<OrganizationMemberEntity> createOrganizationMembers = new HashSet<>();
        // Organization member relationships to delete, KEY: user ID, VALUE: organization ID
        Map<String, Set<String>> deleteOrganizationMembers = Maps.newHashMap();
        Map<UserEntity, Set<OrganizationEntity>> currentUsers = Maps.newHashMap();
        Map<User, Set<OrganizationEntity>> thirdPartyUsers = Maps.newHashMap();
        String targetId = identitySource.getStrategyConfig().getOrganization().getTargetId();
        // Query by username
        List<UserEntity> existUsernames = getByUsernames(thirdPartyUserList.stream().map(User::getUserId).toList());
        // Query by email
        List<UserEntity> existEmails = getEmails(thirdPartyUserList.stream().map(User::getEmail).toList());
        // Query by phone number
        List<UserEntity> existPhones = getPhones(thirdPartyUserList.stream().map(User::getPhone).toList());
        // Build data from the upstream user. This is an update path, so only local users are processed. Missing local users are skipped to reduce complexity and can be handled by incremental pull.
        thirdPartyUserList.forEach(thirdPartyUser -> {

            Optional<UserEntity> optional = userRepository.findByExternalId(thirdPartyUser.getUserId());
            if (optional.isPresent()) {
                UserEntity user = optional.get();
                // Query current system user-organization associations
                List<OrganizationMemberEntity> organizationMembers = organizationMemberRepository.findAllByUserId(user.getId());
                List<String> orgIdList = organizationMembers.stream().map(OrganizationMemberEntity::getOrgId).toList();
                // Query current system user organizations
                List<OrganizationEntity> currentUserOrgList = organizationRepository.findByIdentitySourceIdAndIdIn(identitySource.getId(), orgIdList);
                // Add root organization
                if (orgIdList.contains(targetId)) {
                    organizationRepository.findById(targetId).ifPresent(currentUserOrgList::add);
                }
                currentUsers.put(optional.get(), Sets.newHashSet(currentUserOrgList));
                // Query upstream user organization information
                List<OrganizationEntity> organizationList = organizationRepository.findByIdentitySourceIdAndExternalIdIn(identitySource.getId(), thirdPartyUser.getDeptIdList());
                // Root organization
                if (thirdPartyUser.getDeptIdList().stream().anyMatch(deptId -> deptId.equals(getRootExternalId(identitySource)))) {
                    organizationRepository.findById(targetId).ifPresent(organizationList::add);
                }
                thirdPartyUsers.put(thirdPartyUser, Sets.newHashSet(organizationList));
            }
        });
        thirdPartyUsers.keySet()
            .forEach(thirdPartyUser -> currentUsers.keySet().forEach(currentUser -> {
                if (thirdPartyUser.getUserId().equals(currentUser.getExternalId())) {
                    log.info("处理上游用户修改事件:[{}]开始", thirdPartyUser.getUserId());
                    // Update basic information
                    setUpdateUser(thirdPartyUser, currentUser, identitySource);
                    log.info("上游用户:[{}]对应系统用户:[{}]({})存在,修改本地用户信息：{}", thirdPartyUser.getUserId(), currentUser.getUsername(), currentUser.getId(), JSONObject.toJSONString(currentUser));
                    if (existUsernames.stream().anyMatch(user -> !currentUser.getExternalId().equals(user.getExternalId()) && currentUser.getUsername().equals(user.getUsername()))) {
                        log.info("跳过上游用户:[{{}}]创建,用户名:[{}]在系统已存在", currentUser.getExternalId(),
                                currentUser.getUsername());
                        addRecord(eventTime, identitySource, currentUser.getId(), currentUser.getFullName(), records, SyncStatus.FAIL,
                                IdentitySourceObjectType.USER, IdentitySourceActionType.UPDATE, "用户名 [" + currentUser.getUsername() + "] 已存在");
                        return;
                    }
                    if (StringUtils.isNotBlank(currentUser.getEmail()) && existEmails.stream().anyMatch(user -> !currentUser.getExternalId().equals(user.getExternalId()) && currentUser.getEmail().equals(user.getEmail()))) {
                        log.info("跳过上游用户:[{{}}]创建,邮箱:[{}]在系统已存在", currentUser.getExternalId(),
                                currentUser.getEmail());
                        addRecord(eventTime, identitySource, currentUser.getId(), currentUser.getFullName(), records, SyncStatus.FAIL,
                                IdentitySourceObjectType.USER, IdentitySourceActionType.UPDATE, "邮箱 [" + currentUser.getEmail() + "] 已存在");
                        return;
                    }
                    if (StringUtils.isNotBlank(currentUser.getPhone()) && existPhones.stream().anyMatch(user -> !currentUser.getExternalId().equals(user.getExternalId()) && currentUser.getPhone().equals(user.getPhone()))) {
                        log.info("跳过上游用户:[{{}}]创建,手机号:[{}]在系统已存在", currentUser.getExternalId(),
                                currentUser.getPhone());
                        addRecord(eventTime, identitySource, currentUser.getId(), currentUser.getFullName(), records, SyncStatus.FAIL,
                                IdentitySourceObjectType.USER, IdentitySourceActionType.UPDATE, "手机号 [" + currentUser.getPhone() + "] 已存在");
                        return;
                    }
                    updateUsers.add(currentUser);
                    // Process organization relationships
                    Set<OrganizationEntity> thirdPartyOrganizations = thirdPartyUsers.get(thirdPartyUser);
                    Set<OrganizationEntity> currentOrganizations = currentUsers.get(currentUser);
                    // New relationships to create
                    thirdPartyOrganizations.stream()
                        .filter(item -> !currentOrganizations.contains(item)).collect(toSet())
                        .forEach(organization -> {createOrganizationMember(createOrganizationMembers, currentUser, organization);
                            log.info("上游用户:[{}]对应当前系统用户:[{}]({}),需要创建本地组织[{}]({})与本地成员[{}]({})关系", thirdPartyUser.getUserId(), currentUser.getUsername(), currentUser.getId(), organization.getName(), organization.getId(), currentUser.getUsername(), currentUser.getId());
                        });
                    // Old relationships to delete
                    currentOrganizations.stream()
                        .filter(item -> !thirdPartyOrganizations.contains(item)).collect(toSet())
                        .forEach(organization -> {
                            Set<String> ids = deleteOrganizationMembers.get(currentUser.getId());
                            log.info("上游用户:[{}]对应当前系统用户:[{}]({}),需要删除本地组织[{}]({})与本地成员[{}]({})关系", thirdPartyUser.getUserId(), currentUser.getUsername(), currentUser.getId(), organization.getName(), organization.getId(), currentUser.getUsername(), currentUser.getId());
                            if (org.apache.commons.collections4.CollectionUtils.isNotEmpty(ids)) {
                                ids.add(organization.getId());
                                deleteOrganizationMembers.put(currentUser.getId(), ids);
                                return;
                            }
                            deleteOrganizationMembers.put(currentUser.getId(), Sets.newHashSet(organization.getId()));
                        });
                    // Log
                    addRecord(eventTime, identitySource, currentUser.getId(), currentUser.getFullName(), records, SyncStatus.SUCCESS,
                            IdentitySourceObjectType.USER, IdentitySourceActionType.UPDATE, "修改成功");
                    log.info("处理上游用户修改事件:[{}]结束", thirdPartyUser.getUserId());
                }
                else {
                    addRecord(eventTime, identitySource, thirdPartyUser.getUserId(), Objects.nonNull(thirdPartyUser.getUserDetail()) ? thirdPartyUser.getUserDetail().getName() : null, records, SyncStatus.SKIP,
                            IdentitySourceObjectType.ORGANIZATION, IdentitySourceActionType.UPDATE, "用户[" + (Objects.nonNull(thirdPartyUser.getUserDetail()) ? thirdPartyUser.getUserDetail().getName() : thirdPartyUser.getUserId()) + "]不存在");
                }
            }));
        // Update user information
        userRepository.batchUpdate(updateUsers);
        // Save organization relationship
        organizationMemberRepository.batchSave(Lists.newArrayList(createOrganizationMembers));
        // Delete organization relationship
        deleteOrganizationMembers.keySet()
            .forEach(user -> deleteOrganizationMembers.get(user)
                .forEach(organization -> organizationMemberRepository
                    .deleteByOrgIdAndUserId(organization, user)));
        // Save event record
        identitySourceEventRecordRepository.batchSave(records);
    }

    // User departed
    void leaveUsers(List<String> userIds, LocalDateTime eventTime,
                    IdentitySourceEntity identitySource) {
        List<IdentitySourceEventRecordEntity> records = new ArrayList<>();
        List<UserEntity> list = userRepository.findByExternalIdIn(userIds);
        List<UserEntity> removeUserList = new ArrayList<>();
        list.forEach(user -> {
            log.info("处理上游用户离职事件:[{}]开始", user);
            // Departure deletion
            removeUserList.add(user);
            // Log
            addRecord(eventTime, identitySource, user.getId(), user.getFullName(), records,
                    SyncStatus.SUCCESS, IdentitySourceObjectType.USER, IdentitySourceActionType.DELETE,
                    "删除成功");
            log.info("处理上游用户离职事件:[{}]结束", user);
        });
        // Save event record
        identitySourceEventRecordRepository.batchSave(records);
        // Delete user information
        if (!CollectionUtils.isEmpty(removeUserList)) {
            List<String> deleteUserIds = removeUserList.stream().map(IdEntity::getId).toList();
            // Delete user
            userRepository.deleteAllById(deleteUserIds);
            // Delete user details
            userDetailRepository.deleteAllByUserIdIn(deleteUserIds);
            // Delete organization-user associations
            organizationMemberRepository.deleteAllByUserIdIn(deleteUserIds);
            // Delete user group relationships
            userGroupMemberRepository.deleteAllByUserIdIn(deleteUserIds);
        }
    }

    // Create department
    void createOrganizations(List<Dept> organizations, LocalDateTime eventTime,
                             IdentitySourceEntity identitySource) {
        List<IdentitySourceEventRecordEntity> records = new ArrayList<>();
        List<OrganizationEntity> createOrganizations = new ArrayList<>();
        organizations.forEach(thirdPartyOrganization -> {
            log.info("处理上游部门新增事件:[{}]开始", thirdPartyOrganization.getDeptId());
            // Query parent department
            // If the parent department is the root node, query the current root node configuration in IAM
            Optional<OrganizationEntity> parentOptional;
            if (!thirdPartyOrganization.getParentId()
                    .equals(getRootExternalId(identitySource))) {
                parentOptional = organizationRepository
                        .findByExternalId(thirdPartyOrganization.getParentId());
            } else {
                String targetId = identitySource.getStrategyConfig().getOrganization()
                        .getTargetId();
                parentOptional = organizationRepository.findById(targetId);
            }
            if (parentOptional.isEmpty()) {
                return;
            }
            OrganizationEntity parentOrganization = parentOptional.get();
            // Set leaf=false
            if (parentOrganization.getLeaf()) {
                organizationRepository.updateIsLeaf(parentOrganization.getId(), false);
            }
            // Wrap data
            OrganizationEntity entity = new OrganizationEntity();
            entity.setId(UUID.randomUUID().toString());
            entity.setName(thirdPartyOrganization.getName());
            entity.setCode(RandomStringUtils.randomAlphanumeric(7));
            entity.setType(OrganizationType.DEPARTMENT);
            entity.setParentId(parentOrganization.getId());
            entity.setPath(parentOrganization.getPath() + PATH_SEPARATOR + entity.getId());
            entity.setDisplayPath(
                    parentOrganization.getDisplayPath() + PATH_SEPARATOR + entity.getName());
            entity.setExternalId(thirdPartyOrganization.getDeptId());
            entity.setOrder(thirdPartyOrganization.getOrder());
            entity.setIdentitySourceId(identitySource.getId());
            entity.setDataOrigin(getDataOrigin(identitySource).getType());
            entity.setLeaf(true);
            entity.setEnabled(true);
            // Additional information
            entity.setCreateBy(SYSTEM_DEFAULT_USER_NAME);
            entity.setCreateTime(LocalDateTime.now());
            entity.setUpdateBy(SYSTEM_DEFAULT_USER_NAME);
            entity.setUpdateTime(LocalDateTime.now());
            createOrganizations.add(entity);
            // Log
            addRecord(eventTime, identitySource, entity.getId(), entity.getName(), records,
                    SyncStatus.SUCCESS, IdentitySourceObjectType.ORGANIZATION,
                    IdentitySourceActionType.INSERT, "新增成功");
            log.info("处理上游部门新增事件:[{}]结束", thirdPartyOrganization.getDeptId());
        });
        // Save department information
        organizationRepository.batchSave(createOrganizations);
        // Save event record
        identitySourceEventRecordRepository.batchSave(records);
    }

    // Modify department
    void modifyOrganizations(List<Dept> organizations, LocalDateTime eventTime,
                             IdentitySourceEntity identitySource) {
        List<IdentitySourceEventRecordEntity> records = new ArrayList<>();
        List<OrganizationEntity> updateOrganizations = new ArrayList<>();
        // Query by external ID
        List<OrganizationEntity> list = organizationRepository
                .findByIdentitySourceIdAndExternalIdIn(identitySource.getId(),
                        organizations.stream().map(Dept::getDeptId).toList());
        list.forEach(current -> organizations.forEach(threeParty -> {
            // Map current third-party IDs to upstream departments
            if (current.getExternalId().equals(threeParty.getDeptId())) {

                // If the parent department changed, update child departments and their display paths; if the name changed, update child display names
                // If the parent department is the root node, query the current root node configuration in IAM
                Optional<OrganizationEntity> parentOptional;
                if (!threeParty.getParentId()
                        .equals(getRootExternalId(identitySource))) {
                    parentOptional = organizationRepository
                            .findByExternalId(threeParty.getParentId());
                } else {
                    String targetId = identitySource.getStrategyConfig().getOrganization()
                            .getTargetId();
                    parentOptional = organizationRepository.findById(targetId);
                }
                if (parentOptional.isEmpty()) {
                    return;
                }
                // Parent department mismatch
                OrganizationEntity parentOrganization = parentOptional.get();
                if (!StringUtils.equals(parentOrganization.getId(), current.getParentId())
                        || !StringUtils.equals(current.getName(), threeParty.getName())) {
                    current.setParentId(parentOrganization.getId());
                    current
                            .setPath(parentOrganization.getPath() + PATH_SEPARATOR + current.getId());
                    current.setDisplayPath(parentOrganization.getDisplayPath() + PATH_SEPARATOR
                            + threeParty.getName());
                    // Recursively assemble child data
                    updateOrganizations.addAll(processSubOrganizations(current));
                    // If the current organization's parent no longer has children, set leaf=true
                    List<OrganizationEntity> subOrganizations = organizationRepository
                            .findByParentId(parentOrganization.getId());
                    if (subOrganizations.stream().map(i -> i.getId().equals(current.getId()))
                            .findAny().isEmpty()) {
                        organizationRepository.updateIsLeaf(parentOrganization.getId(), true);
                    }
                }
                if (parentOrganization.getLeaf()) {
                    organizationRepository.updateIsLeaf(parentOrganization.getId(), false);
                }
                // Modify basic information
                current.setName(threeParty.getName());
                current.setOrder(threeParty.getOrder());
                updateOrganizations.add(current);
                // Log
                addRecord(eventTime, identitySource, current.getId(), current.getName(), records,
                        SyncStatus.SUCCESS, IdentitySourceObjectType.ORGANIZATION,
                        IdentitySourceActionType.UPDATE, "修改成功");
            } else {
                addRecord(eventTime, identitySource, current.getId(), current.getName(), records,
                        SyncStatus.SKIP, IdentitySourceObjectType.ORGANIZATION,
                        IdentitySourceActionType.UPDATE, "部门[" + threeParty.getName() + "]不存在");
            }
        }));
        // Batch save department information
        organizationRepository.batchUpdate(updateOrganizations);
        // Save event record
        identitySourceEventRecordRepository.batchSave(records);
    }

    // Delete department
    void removeOrganizations(List<String> organizationIds, LocalDateTime eventTime,
                             IdentitySourceEntity identitySource) {
        List<IdentitySourceEventRecordEntity> records = new ArrayList<>();
        List<OrganizationEntity> list = organizationRepository
                .findByIdentitySourceIdAndExternalIdIn(identitySource.getId(), organizationIds);
        list.forEach(organization -> {
            log.info("处理上游部门删除事件:[{}]开始", organization.getId());
            IdentitySourceEventRecordEntity record = new IdentitySourceEventRecordEntity();
            record.setObjectId(organization.getId());
            record.setIdentitySourceId(identitySource.getId());
            record.setObjectName(organization.getName());
            record.setObjectType(IdentitySourceObjectType.ORGANIZATION);
            record.setActionType(IdentitySourceActionType.DELETE);
            record.setEventTime(eventTime);
            record.setStatus(SyncStatus.SUCCESS);
            // Additional information
            record.setId(UUID.randomUUID().toString());
            record.setCreateBy(SYSTEM_DEFAULT_USER_NAME);
            record.setCreateTime(LocalDateTime.now());
            record.setUpdateBy(SYSTEM_DEFAULT_USER_NAME);
            record.setUpdateTime(LocalDateTime.now());
            records.add(record);
            log.info("处理上游部门删除事件:[{}]结束", organization.getId());
        });
        // Batch delete
        organizationRepository.deleteAllById(
                list.stream().map(OrganizationEntity::getId).collect(Collectors.toSet()));
        // Save event record
        identitySourceEventRecordRepository.batchSave(records);
    }

    private List<OrganizationEntity> processSubOrganizations(OrganizationEntity parent) {
        List<OrganizationEntity> list = new ArrayList<>();
        // Recursively assemble child data
        List<OrganizationEntity> subOrganizations = organizationRepository
                .findByParentId(parent.getId());
        subOrganizations.forEach(sub -> {
            sub.setParentId(parent.getId());
            sub.setPath(parent.getPath() + PATH_SEPARATOR + sub.getId());
            sub.setDisplayPath(parent.getDisplayPath() + PATH_SEPARATOR + sub.getName());
            list.add(sub);
            list.addAll(processSubOrganizations(sub));
        });
        return list;
    }

    protected List<UserEntity> getPhones(List<String> userPhonelist) {
        return userRepository.findAllByPhoneIn(userPhonelist);
    }

    protected List<UserEntity> getEmails(List<String> userEmialList) {
        return userRepository.findAllByEmailIn(userEmialList);
    }

    protected List<UserEntity> getByUsernames(List<String> userNameList) {
        return userRepository.findAllByUsernameIn(userNameList);
    }
}
