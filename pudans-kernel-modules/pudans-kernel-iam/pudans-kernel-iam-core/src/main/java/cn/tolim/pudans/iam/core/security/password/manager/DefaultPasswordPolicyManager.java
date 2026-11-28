package cn.tolim.pudans.iam.core.security.password.manager;

import cn.tolim.pudans.iam.communal.entity.account.UserEntity;
import cn.tolim.pudans.iam.communal.entity.account.UserHistoryPasswordEntity;
import cn.tolim.pudans.iam.communal.entity.setting.SettingEntity;
import cn.tolim.pudans.iam.communal.repository.account.UserHistoryPasswordRepository;
import cn.tolim.pudans.iam.communal.repository.account.UserRepository;
import cn.tolim.pudans.iam.communal.repository.setting.SettingRepository;
import cn.tolim.pudans.iam.core.setting.PasswordPolicySettingConstants;
import cn.tolim.pudans.iam.infra.security.password.PasswordPolicyManager;
import cn.tolim.pudans.iam.infra.security.password.PasswordValidator;
import cn.tolim.pudans.iam.infra.security.password.enums.PasswordComplexityRule;
import cn.tolim.pudans.iam.infra.security.password.validator.*;
import cn.tolim.pudans.iam.infra.security.password.weak.PasswordWeakLib;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;

import static cn.tolim.pudans.iam.core.setting.PasswordPolicySettingConstants.*;
import static cn.tolim.pudans.iam.infra.repository.base.BaseEntity.LAST_MODIFIED_TIME;


public class DefaultPasswordPolicyManager implements PasswordPolicyManager {

    // Password validators
    private final List<PasswordValidator> providers;
    // User repository
    private final UserRepository userRepository;
    // User history password repository
    private final UserHistoryPasswordRepository userHistoryPasswordRepository;
    // Setting repository
    private final SettingRepository settingRepository;
    // Weak password dictionary
    private final PasswordWeakLib passwordWeakLib;
    // Password encoder
    private final PasswordEncoder passwordEncoder;

    public DefaultPasswordPolicyManager(UserRepository userRepository,
                                        UserHistoryPasswordRepository userHistoryPasswordRepository,
                                        SettingRepository settingRepository,
                                        PasswordWeakLib passwordWeakLib,
                                        PasswordEncoder passwordEncoder) {
        this.userHistoryPasswordRepository = userHistoryPasswordRepository;
        this.userRepository = userRepository;
        this.settingRepository = settingRepository;
        this.passwordWeakLib = passwordWeakLib;
        this.passwordEncoder = passwordEncoder;
        providers = List.of(
                getPasswordIllegalSequenceValidator(),
                getPasswordContinuousSameCharValidator(),
                getPasswordLengthValidator(),
                getPasswordComplexityRuleValidator(),
                getWeakPasswordValidator());
    }

    // Validates the password for the authenticated principal.
    @Override
    public void validate(@NonNull Object principal, String password) {
        if (principal instanceof UserEntity) {
            String userId = ((UserEntity) principal).getId();
            List<PasswordValidator> validators = new ArrayList<>(providers);
            validators.add(getPasswordIncludeUserInfoValidator(userId));
            validators.add(getHistoryPasswordValidator(userId));
            validators.forEach(passwordValidator -> passwordValidator.validate(password));
        }
    }

    // Returns the validator that rejects passwords containing user information.
    private PasswordIncludeUserInfoValidator getPasswordIncludeUserInfoValidator(String userId) {
        SettingEntity setting = settingRepository
                .findByName(PasswordPolicySettingConstants.PASSWORD_POLICY_ACCOUNT_CHECK);
        boolean enabled = Objects.isNull(setting)
                ? Boolean.parseBoolean(PasswordPolicySettingConstants.PASSWORD_POLICY_DEFAULT_SETTINGS
                .get(PasswordPolicySettingConstants.PASSWORD_POLICY_ACCOUNT_CHECK))
                : Boolean.parseBoolean(setting.getValue());
        if (enabled) {
            Optional<UserEntity> optionalUser = userRepository.findById(userId);
            if (optionalUser.isPresent()) {
                UserEntity user = optionalUser.get();
                return new PasswordIncludeUserInfoValidator(user.getFullName(), user.getNickName(), user.getUsername(), user.getPhone(), user.getEmail());
            }
        }
        return new PasswordIncludeUserInfoValidator(false);
    }

    // Returns the validator that rejects recently used passwords.
    private HistoryPasswordValidator getHistoryPasswordValidator(String userId) {
        SettingEntity historyCipherCheck = settingRepository
                .findByName(PasswordPolicySettingConstants.PASSWORD_POLICY_HISTORY_PASSWORD_CHECK);
        boolean enabled = Objects.isNull(historyCipherCheck)
                ? Boolean.parseBoolean(PasswordPolicySettingConstants.PASSWORD_POLICY_DEFAULT_SETTINGS
                .get(PASSWORD_POLICY_HISTORY_PASSWORD_CHECK))
                : Boolean.parseBoolean(historyCipherCheck.getValue());
        if (enabled) {
            SettingEntity historyCipherCheckCount = settingRepository.findByName(
                    PasswordPolicySettingConstants.PASSWORD_POLICY_HISTORY_PASSWORD_CHECK_COUNT);
            Integer count = Objects.isNull(historyCipherCheckCount)
                    ? Integer.valueOf(PasswordPolicySettingConstants.PASSWORD_POLICY_DEFAULT_SETTINGS
                    .get(PASSWORD_POLICY_HISTORY_PASSWORD_CHECK_COUNT))
                    : Integer.valueOf(historyCipherCheckCount.getValue());
            Page<UserHistoryPasswordEntity> entities = userHistoryPasswordRepository.findAll(
                    Example.of(new UserHistoryPasswordEntity().setUserId(userId)),
                    PageRequest.of(0, count, Sort.by(Sort.Direction.DESC, LAST_MODIFIED_TIME)));
            // Build password history validator
            return new HistoryPasswordValidator(
                    entities.getContent().stream().map(UserHistoryPasswordEntity::getPassword).toList(),
                    passwordEncoder);
        }
        return new HistoryPasswordValidator(false);
    }

    // Returns the configured password length validator.
    private PasswordLengthValidator getPasswordLengthValidator() {
        // Minimum length
        SettingEntity leastLengthEntity = settingRepository
                .findByName(PasswordPolicySettingConstants.PASSWORD_POLICY_LEAST_LENGTH);
        Integer leastLength = Objects.isNull(leastLengthEntity)
                ? Integer.valueOf(PasswordPolicySettingConstants.PASSWORD_POLICY_DEFAULT_SETTINGS
                .get(PASSWORD_POLICY_LEAST_LENGTH))
                : Integer.valueOf(leastLengthEntity.getValue());
        // Maximum length
        SettingEntity biggestLengthEntity = settingRepository
                .findByName(PasswordPolicySettingConstants.PASSWORD_POLICY_BIGGEST_LENGTH);
        Integer biggestLength = Objects.isNull(biggestLengthEntity)
                ? Integer.valueOf(PasswordPolicySettingConstants.PASSWORD_POLICY_DEFAULT_SETTINGS
                .get(PASSWORD_POLICY_BIGGEST_LENGTH))
                : Integer.valueOf(biggestLengthEntity.getValue());
        return new PasswordLengthValidator(leastLength, biggestLength);
    }

    // Returns the configured password complexity validator.
    private PasswordComplexityRuleValidator getPasswordComplexityRuleValidator() {
        SettingEntity setting = settingRepository.findByName(PASSWORD_POLICY_COMPLEXITY);
        String complexityRule = Objects.isNull(setting)
                ? PASSWORD_POLICY_DEFAULT_SETTINGS.get(PASSWORD_POLICY_COMPLEXITY)
                : setting.getValue();
        PasswordComplexityRule rule = PasswordComplexityRule.getType(complexityRule);
        return new PasswordComplexityRuleValidator(rule);
    }

    // Returns the configured weak password validator.
    private WeakPasswordValidator getWeakPasswordValidator() {
        SettingEntity setting = settingRepository
                .findByName(PasswordPolicySettingConstants.PASSWORD_POLICY_WEAK_PASSWORD_CHECK);
        String enable = Objects.isNull(setting)
                ? PasswordPolicySettingConstants.PASSWORD_POLICY_DEFAULT_SETTINGS
                .get(PASSWORD_POLICY_WEAK_PASSWORD_CHECK)
                : setting.getValue();
        if (Boolean.parseBoolean(enable)) {
            ArrayList<String> list = new ArrayList<>();
            // Custom weak password
            SettingEntity customWeakCipher = settingRepository
                    .findByName(PASSWORD_POLICY_CUSTOM_WEAK_PASSWORD);
            if (!Objects.isNull(customWeakCipher)) {
                list.addAll(Arrays.asList(customWeakCipher.getValue().split("\n")));
            }
            // System weak passwords
            list.addAll(passwordWeakLib.getWordList());
            // Create WeakPasswordValidator
            return new WeakPasswordValidator(list);
        }
        return new WeakPasswordValidator(false);
    }

    // Validates the password for the given user ID.
    @Override
    public void validate(@NonNull String userId, String password) {
        List<PasswordValidator> validators = new ArrayList<>(providers);
        validators.add(getPasswordIncludeUserInfoValidator(userId));
        validators.add(getHistoryPasswordValidator(userId));
        validators.forEach(passwordValidator -> passwordValidator.validate(password));
    }

    // Returns the validator for consecutive identical characters.
    private PasswordContinuousSameCharValidator getPasswordContinuousSameCharValidator() {
        SettingEntity setting = settingRepository.findByName(PASSWORD_POLICY_NOT_SAME_CHARS);
        String rule = Objects.isNull(setting)
                ? PasswordPolicySettingConstants.PASSWORD_POLICY_DEFAULT_SETTINGS
                .get(PASSWORD_POLICY_NOT_SAME_CHARS)
                : setting.getValue();
        return new PasswordContinuousSameCharValidator(Integer.valueOf(rule));
    }

    // Returns the validator for illegal password sequences.
    private PasswordIllegalSequenceValidator getPasswordIllegalSequenceValidator() {
        SettingEntity setting = settingRepository
                .findByName(PASSWORD_POLICY_ILLEGAL_SEQUENCE_CHECK);
        String enable = Objects.isNull(setting)
                ? PASSWORD_POLICY_DEFAULT_SETTINGS.get(PASSWORD_POLICY_ILLEGAL_SEQUENCE_CHECK)
                : setting.getValue();
        return new PasswordIllegalSequenceValidator(Boolean.parseBoolean(enable));
    }
}
