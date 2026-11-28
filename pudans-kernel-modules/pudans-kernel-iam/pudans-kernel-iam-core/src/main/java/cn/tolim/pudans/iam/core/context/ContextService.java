package cn.tolim.pudans.iam.core.context;

import cn.tolim.pudans.iam.communal.entity.setting.SettingEntity;
import cn.tolim.pudans.iam.communal.entity.setting.config.SmsConfig;
import cn.tolim.pudans.iam.communal.repository.setting.SettingRepository;
import cn.tolim.pudans.iam.core.setting.SecuritySettingConstants;
import cn.tolim.pudans.iam.infra.autoconfiguration.SupportProperties;
import cn.tolim.pudans.iam.infra.context.ApplicationContextService;
import cn.tolim.pudans.iam.infra.exception.PudansException;
import cn.tolim.pudans.iam.infra.security.util.ContentSecurityPolicyUtils;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shapesecurity.salvation2.Directives.SourceExpressionDirective;
import com.shapesecurity.salvation2.FetchDirectiveKind;
import com.shapesecurity.salvation2.Policy;
import com.shapesecurity.salvation2.Values.Host;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import static cn.tolim.pudans.iam.communal.constant.AuthnConstants.FE_LOGIN;
import static cn.tolim.pudans.iam.communal.constant.ConfigBeanNameConstants.DEFAULT_SECURITY_FILTER_CHAIN;
import static cn.tolim.pudans.iam.core.setting.MessageSettingConstants.MESSAGE_SMS_PROVIDER;
import static cn.tolim.pudans.iam.core.setting.SecuritySettingConstants.*;


public final class ContextService {

    private static final Logger logger = LoggerFactory.getLogger(ContextService.class);

    // Get SMS configuration used for template resolution
    // Returns: SmsConfig
    public static SmsConfig getSmsProviderConfig() {
        SettingEntity setting = getSettingRepository().findByName(MESSAGE_SMS_PROVIDER);
        if (!Objects.isNull(setting) && StringUtils.isNotBlank(setting.getValue())) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                // Specify the serialized input type
                objectMapper.activateDefaultTyping(objectMapper.getPolymorphicTypeValidator(),
                        ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
                return objectMapper.readValue(setting.getValue(), SmsConfig.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        return new SmsConfig();
    }

    // Get continuous login failure duration
    // Returns: Integer
    public static Integer getLoginFailureDuration() {
        SettingEntity setting = getSettingRepository()
                .findByName(SECURITY_DEFENSE_POLICY_LOGIN_FAILURE_DURATION);
        if (Objects.isNull(setting)) {
            return Integer.valueOf(SecuritySettingConstants.SECURITY_DEFENSE_POLICY_DEFAULT_SETTINGS
                    .get(SECURITY_DEFENSE_POLICY_LOGIN_FAILURE_DURATION));
        }
        return Integer.valueOf(setting.getValue());
    }

    // Get continuous login failure count
    // Returns: Integer
    public static Integer getLoginFailureCount() {
        SettingEntity setting = getSettingRepository()
                .findByName(SECURITY_DEFENSE_POLICY_FAILURE_COUNT);
        if (Objects.isNull(setting)) {
            return Integer.valueOf(SECURITY_DEFENSE_POLICY_DEFAULT_SETTINGS
                    .get(SECURITY_DEFENSE_POLICY_FAILURE_COUNT));
        }
        return Integer.valueOf(setting.getValue());
    }

    // Get captcha validity period
    // Returns: Integer Seconds
    public static Integer getCodeValidTime() {
        SettingEntity setting = getSettingRepository().findByName(VERIFY_CODE_VALID_TIME);
        if (Objects.isNull(setting)) {
            return Integer.valueOf(SECURITY_BASIC_DEFAULT_SETTINGS.get(VERIFY_CODE_VALID_TIME));
        }
        return Integer.valueOf(setting.getValue());
    }

    // Get auto-unlock time
    // Returns: Integer Seconds
    public static Integer getAutoUnlockTime() {
        SettingEntity setting = getSettingRepository()
                .findByName(SECURITY_DEFENSE_POLICY_AUTO_UNLOCK_TIME);
        if (Objects.isNull(setting)) {
            return Integer.valueOf(SECURITY_DEFENSE_POLICY_DEFAULT_SETTINGS
                    .get(SECURITY_DEFENSE_POLICY_AUTO_UNLOCK_TIME));
        }
        return Integer.valueOf(setting.getValue());
    }

    // Add img-src host to Content Security Policy
    // Parameter value: String
    public static void addImgSrcHostContentSecurityPolicy(String value) {
        SettingEntity settingEntity = getSettingRepository()
                .findByName(SECURITY_DEFENSE_POLICY_CONTENT_SECURITY_POLICY);
        String contentSecurityPolicy;
        if (Objects.isNull(settingEntity)) {
            settingEntity = new SettingEntity();
            settingEntity.setName(SECURITY_DEFENSE_POLICY_CONTENT_SECURITY_POLICY);
            contentSecurityPolicy = SECURITY_DEFENSE_POLICY_DEFAULT_SETTINGS
                    .get(SECURITY_DEFENSE_POLICY_CONTENT_SECURITY_POLICY);
        } else {
            contentSecurityPolicy = settingEntity.getValue();
        }
        Policy parse = ContentSecurityPolicyUtils.parse(contentSecurityPolicy);
        SourceExpressionDirective directive = parse.getFetchDirective(FetchDirectiveKind.ImgSrc)
                .orElseThrow();
        Host host = Host.parseHost(value).orElseThrow();
        directive.removeHost(host);
        directive.addHost(host, (severity, message) -> {
            logger.error("添加操作内容安全策略 img-src host 异常：{}", message);
            throw new PudansException(message);
        });
        settingEntity.setValue(parse.toString());
        getSettingRepository().save(settingEntity);
        ApplicationContextService.refresh(DEFAULT_SECURITY_FILTER_CHAIN);
    }

    // Get console base URL
    // Returns: String
    public static String getConsolePublicBaseUrl() {
        return getSupportProperties().getServer().getConsolePublicBaseUrl();
    }

    // Get portal base URL
    // Returns: String
    public static String getPortalPublicBaseUrl() {
        return getSupportProperties().getServer().getPortalPublicBaseUrl();
    }

    // Get portal login URL
    // Returns: String
    public static String getPortalLoginUrl() {
        return getSupportProperties().getServer().getPortalPublicBaseUrl() + FE_LOGIN;
    }

    public static SettingRepository getSettingRepository() {
        return ApplicationContextService.getBean(SettingRepository.class);
    }

    public static SupportProperties getSupportProperties() {
        return ApplicationContextService.getBean(SupportProperties.class);
    }
}
