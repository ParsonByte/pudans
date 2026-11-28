package cn.tolim.pudans.iam.synchronizer.processor;

import cn.tolim.pudans.iam.communal.entity.account.OrganizationEntity;
import cn.tolim.pudans.iam.communal.entity.account.OrganizationMemberEntity;
import cn.tolim.pudans.iam.communal.entity.account.UserEntity;
import cn.tolim.pudans.iam.communal.entity.identitysource.IdentitySourceEntity;
import cn.tolim.pudans.iam.communal.entity.identitysource.config.StrategyConfig;
import cn.tolim.pudans.iam.communal.enums.MailType;
import cn.tolim.pudans.iam.communal.enums.SyncStatus;
import cn.tolim.pudans.iam.communal.enums.UserStatus;
import cn.tolim.pudans.iam.communal.enums.ViewContentType;
import cn.tolim.pudans.iam.communal.enums.identitysource.IdentitySourceActionType;
import cn.tolim.pudans.iam.communal.repository.identitysource.IdentitySourceRepository;
import cn.tolim.pudans.iam.communal.repository.identitysource.IdentitySourceSyncHistoryRepository;
import cn.tolim.pudans.iam.communal.repository.identitysource.IdentitySourceSyncRecordRepository;
import cn.tolim.pudans.iam.communal.storage.Storage;
import cn.tolim.pudans.iam.core.message.MsgVariable;
import cn.tolim.pudans.iam.core.message.mail.MailMsgEventPublish;
import cn.tolim.pudans.iam.identitysource.core.domain.User;
import cn.tolim.pudans.iam.identitysource.core.domain.UserDetail;
import cn.tolim.pudans.iam.identitysource.core.exception.IdentitySourceNotExistException;
import cn.tolim.pudans.iam.identitysource.core.provider.IdentitySourceProviderMetadata;
import cn.tolim.pudans.iam.infra.security.password.PasswordGenerator;
import cn.tolim.pudans.iam.infra.security.userdetails.DataOrigin;
import cn.tolim.pudans.iam.synchronizer.provider.IdentitySourceProviderResolver;
import jakarta.persistence.EntityManager;
import lombok.Builder;
import lombok.Cleanup;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static cn.tolim.pudans.iam.communal.enums.UserStatus.DISABLED;
import static cn.tolim.pudans.iam.communal.enums.UserStatus.ENABLED;
import static cn.tolim.pudans.iam.infra.constant.IamConstants.SYSTEM_DEFAULT_USER_NAME;


@Slf4j
public class AbstractIdentitySourcePostProcessor {

    // TransactionDefinition
    protected final TransactionDefinition transactionDefinition;
    // PlatformTransactionManager
    protected final PlatformTransactionManager platformTransactionManager;
    // EntityManager
    protected final EntityManager entityManager;
    // Identity source repository
    protected final IdentitySourceRepository identitySourceRepository;
    // IdentitySourceSyncHistoryRepository
    protected final IdentitySourceSyncHistoryRepository identitySourceSyncHistoryRepository;
    // IdentitySourceSyncRecordRepository
    protected final IdentitySourceSyncRecordRepository identitySourceSyncRecordRepository;
    // PasswordEncoder
    private final PasswordEncoder passwordEncoder;
    // Password generator
    private final PasswordGenerator passwordGenerator;
    // Mail message event publishing
    private final MailMsgEventPublish mailMsgEventPublish;
    // Storage
    private final Storage storage;
    // Identity source provider resolver
    protected final IdentitySourceProviderResolver identitySourceProviderResolver;

    public AbstractIdentitySourcePostProcessor(MailMsgEventPublish mailMsgEventPublish,
                                               PasswordEncoder passwordEncoder,
                                               PasswordGenerator passwordGenerator,
                                               TransactionDefinition transactionDefinition,
                                               PlatformTransactionManager platformTransactionManager,
                                               EntityManager entityManager,
                                               IdentitySourceRepository identitySourceRepository,
                                               IdentitySourceSyncHistoryRepository identitySourceSyncHistoryRepository,
                                               IdentitySourceSyncRecordRepository identitySourceSyncRecordRepository,
                                               Storage storage,
                                               IdentitySourceProviderResolver identitySourceProviderResolver) {
        this.mailMsgEventPublish = mailMsgEventPublish;
        this.passwordEncoder = passwordEncoder;
        this.passwordGenerator = passwordGenerator;
        this.transactionDefinition = transactionDefinition;
        this.platformTransactionManager = platformTransactionManager;
        this.entityManager = entityManager;
        this.identitySourceRepository = identitySourceRepository;
        this.identitySourceSyncHistoryRepository = identitySourceSyncHistoryRepository;
        this.identitySourceSyncRecordRepository = identitySourceSyncRecordRepository;
        this.storage = storage;
        this.identitySourceProviderResolver = identitySourceProviderResolver;
    }

    // Create organization-user relationship
    protected static void createOrganizationMember(Set<OrganizationMemberEntity> createOrganizationMembers,
                                                   UserEntity currentUser,
                                                   OrganizationEntity organization) {
        OrganizationMemberEntity entity = new OrganizationMemberEntity(UUID.randomUUID().toString(),
                organization.getId(), currentUser.getId());
        entity.setCreateBy(SYSTEM_DEFAULT_USER_NAME);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateBy(SYSTEM_DEFAULT_USER_NAME);
        entity.setUpdateTime(LocalDateTime.now());
        createOrganizationMembers.add(entity);
    }

    // Compare users
    protected Boolean equalsUser(User user, UserEntity entity, String identitySourceId) {
        if (user == null || user.getUserDetail() == null) {
            return false;
        }
        UserDetail userDetail = user.getUserDetail();
        return StringUtils.equals(entity.getExternalId(), user.getUserId())
                && StringUtils.equals(Objects.isNull(entity.getIdentitySourceId()) ? null:entity.getIdentitySourceId(), identitySourceId)
                && StringUtils.equals(entity.getPhoneAreaCode(), user.getPhoneAreaCode())
                // The upstream value "" becomes null on save because the database has a unique index; account for this when matching.
                && StringUtils.equals(StringUtils.isBlank(entity.getEmail()) ? null : entity.getEmail(), StringUtils.isBlank(user.getEmail()) ? null : user.getEmail())
                // The upstream value "" becomes null on save because the database has a unique index; account for this when matching.
                && StringUtils.equals(StringUtils.isBlank(entity.getPhone()) ? null : entity.getPhone(), StringUtils.isBlank(user.getPhone()) ? null : user.getPhone())
                && Objects.equals(UserStatus.ENABLED.equals(entity.getStatus()), user.getActive())
                && StringUtils.equals(entity.getFullName(),userDetail.getName())
                && StringUtils.equals(entity.getNickName(),userDetail.getNickName());
    }

    // Set latest user values
    protected void setUpdateUser(User user, UserEntity entity,
                                 IdentitySourceEntity identitySource) {
        if (StringUtils.isEmpty(entity.getAvatar())) {
            try {
                convertAvatar(user.getAvatar(), entity);
            } catch (Exception e) {
                log.error("修改用户, 同步头像发生异常: 用户: [{}], 异常: [{}]", user.getUserId(), e.getMessage());
            }
        }

        StrategyConfig.User strategyConfig = identitySource.getStrategyConfig().getUser();
        boolean enabled = false;
        if (!Objects.isNull(strategyConfig)) {
            enabled = Boolean.TRUE.equals(strategyConfig.getEnabled());
        }
        entity.setStatus(enabled || user.getActive() ? ENABLED : DISABLED);
        entity.setPhone(StringUtils.isEmpty(user.getPhone()) ? null : user.getPhone());
        entity.setPhoneAreaCode(user.getPhoneAreaCode());
        entity.setEmail(StringUtils.isEmpty(user.getEmail()) ? null : user.getEmail());
        entity.setNickName(user.getUserDetail().getNickName());
        entity.setFullName(user.getUserDetail().getName());
        entity.setIdentitySourceId(identitySource.getId());
    }

    // Convert upstream user to system UserEntity
    protected UserEntity thirdPartyUserToUserEntity(User user,
                                                    IdentitySourceEntity identitySource) {
        DataOrigin dataOrigin = getDataOrigin(identitySource);
        StrategyConfig.User strategyConfig = identitySource.getStrategyConfig().getUser();
        boolean enabled = false;
        String defaultPassword = null;
        if (!Objects.isNull(strategyConfig)) {
            enabled = Boolean.TRUE.equals(strategyConfig.getEnabled());
            defaultPassword = Objects.toString(strategyConfig.getDefaultPassword());
        }
        if (StringUtils.isBlank(defaultPassword)) {
            defaultPassword = passwordGenerator.generatePassword();
        }
        // Wrap
        UserEntity entity = new UserEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setExternalId(user.getUserId());
        String avatar = user.getAvatar();
        if (StringUtils.isNotEmpty(avatar)) {
            try {
                convertAvatar(user.getAvatar(), entity);
            }
            catch (Exception e) {
                log.error("创建用户, 同步头像发生异常: 用户: [{}], 异常: [{}]", user.getUserId(), e.getMessage());
            }
        }
        entity.setEmail(StringUtils.isEmpty(user.getEmail()) ? null : user.getEmail());
        entity.setPhone(StringUtils.isEmpty(user.getPhone()) ? null : user.getPhone());
        entity.setFullName(user.getUserDetail().getName());
        entity.setNickName(user.getUserDetail().getNickName());
        entity.setExpireDate(LocalDate.of(2116,12,31));
        entity.setPhoneAreaCode(user.getPhoneAreaCode());
        // If the feature is enabled but the upstream user is disabled, disable the user
        entity.setStatus(enabled || user.getActive() ? ENABLED : DISABLED);
        entity.setUsername(user.getUserId());
        entity.setDataOrigin(dataOrigin.getType());
        entity.setIdentitySourceId(identitySource.getId());
        entity.setPassword(passwordEncoder.encode(defaultPassword));
        entity.setPasswordPlainText(defaultPassword);

        // Required fields
        entity.setCreateBy(SYSTEM_DEFAULT_USER_NAME);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateBy(SYSTEM_DEFAULT_USER_NAME);
        entity.setUpdateTime(LocalDateTime.now());
        return entity;
    }

    // Convert upstream avatar to an IAM system address
    private void convertAvatar(String avatar, UserEntity entity) throws Exception {
        // Upload to object storage
        URL url = new URL(avatar);
        URLConnection urlConnection = url.openConnection();
        String contentType = urlConnection.getContentType();
        String name = FilenameUtils.getBaseName(avatar);
        String suffix = ViewContentType.getSuffix(contentType);
        @Cleanup
        InputStream inputStream = urlConnection.getInputStream();
        if (Objects.nonNull(inputStream)) {
            entity.setAvatar(storage.upload(name + "." + suffix, inputStream));
        }
    }

    // Get identity source policy configuration
    protected IdentitySourceEntity getIdentitySource(String id) {
        Optional<IdentitySourceEntity> optional = identitySourceRepository.findById(id);
        if (optional.isEmpty()) {
            throw new IdentitySourceNotExistException();
        }
        return optional.get();
    }

    // Get identity source policy configuration
    protected StrategyConfig getStrategyConfig(String id) {
        IdentitySourceEntity entity = getIdentitySource(id);
        return entity.getStrategyConfig();
    }

    // Get root organization external ID
    protected String getRootExternalId(IdentitySourceEntity identitySource) {
        return identitySourceProviderResolver.resolve(identitySource).rootExternalId();
    }

    // Get data source
    protected DataOrigin getDataOrigin(IdentitySourceEntity identitySource) {
        IdentitySourceProviderMetadata metadata =
                identitySourceProviderResolver.resolve(identitySource);
        return new DataOrigin(metadata.dataOriginType(), metadata.dataOriginName());
    }

    // Batch send welcome SMS/email (password notification)
    protected void publishMessage(List<UserEntity> users) {
        try {
            users.forEach(thirdPartyUser -> {
                log.info("批量发送邮件欢迎信息:[{}]开始, email: {}, phone: {}", thirdPartyUser.getUsername(),
                        thirdPartyUser.getEmail(), thirdPartyUser.getPhone());
                if (StringUtils.isNotEmpty(thirdPartyUser.getEmail())) {
                    Map<String, Object> parameter = new HashMap<>(16);
                    parameter.put(MsgVariable.PASSWORD, thirdPartyUser.getPasswordPlainText());
                    mailMsgEventPublish.publish(MailType.RESET_PASSWORD_CONFIRM,
                            thirdPartyUser.getEmail(), parameter);
                }
            });
        } catch (Exception e) {
            log.error("身份源同步用户发送邮件异常:[{}]", e.getMessage(), e);
        }
    }

    @Data
    @Builder
    protected static class SkipUser {

        // User
        private UserEntity user;

        // Operation type
        private IdentitySourceActionType actionType;

        // Status
        private SyncStatus status;

        // Reason
        private String reason;
    }

}
