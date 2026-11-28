package cn.tolim.pudans.iam.protocol.saml.application;

import cn.tolim.pudans.audit.context.AuditContext;
import cn.tolim.pudans.iam.appkernel.core.exception.AppNotExistException;
import cn.tolim.pudans.iam.communal.entity.app.AppEntity;
import cn.tolim.pudans.iam.communal.enums.app.AppCertUsingType;
import cn.tolim.pudans.iam.communal.enums.app.AppProtocol;
import cn.tolim.pudans.iam.communal.enums.app.AppType;
import cn.tolim.pudans.iam.communal.enums.app.AuthorizationType;
import cn.tolim.pudans.iam.communal.repository.app.AppAccessPolicyRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppAccountRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppCertRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppGroupAssociationRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppRepository;
import cn.tolim.pudans.iam.infra.exception.PudansException;
import cn.tolim.pudans.iam.infra.validation.ValidationUtils;
import cn.tolim.pudans.iam.protocol.saml.constant.SamlBindingType;
import cn.tolim.pudans.iam.protocol.saml.constant.SamlNameIdFormat;
import cn.tolim.pudans.iam.protocol.saml.converter.AppSamlConfigConverter;
import cn.tolim.pudans.iam.protocol.saml.model.SamlConfigData;
import cn.tolim.pudans.iam.protocol.saml.pojo.AppSamlSaveConfigParam;
import cn.tolim.pudans.iam.protocol.saml.store.SamlConfigStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cn.tolim.pudans.iam.protocol.saml.constant.SamlProtocolConstants.DEFAULT_ASSERTION_TIME_TO_LIVE_SECONDS;
import static cn.tolim.pudans.iam.protocol.saml.constant.SamlProtocolConstants.DEFAULT_CLOCK_SKEW_SECONDS;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;


@Component
public class SamlStandardApplicationServiceImpl extends AbstractSamlApplicationService {

    private static final String ICON = "data:image/svg+xml;base64,"
            + "PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI2NCIgaGVpZ2h0PSI2NCIg"
            + "dmlld0JveD0iMCAwIDY0IDY0Ij48cmVjdCB3aWR0aD0iNjQiIGhlaWdodD0iNjQiIHJ4PSIxMiIgZmls"
            + "bD0iIzFmNmZlYiIvPjx0ZXh0IHg9IjMyIiB5PSI0MCIgZm9udC1mYW1pbHk9IkFyaWFsIiBmb250LXNp"
            + "emU9IjE4IiBmb250LXdlaWdodD0iYm9sZCIgZmlsbD0iI2ZmZmZmZiIgdGV4dC1hbmNob3I9Im1pZGRs"
            + "ZSI+U0FNTDwvdGV4dD48L3N2Zz4=";

    private final Logger logger = LoggerFactory.getLogger(SamlStandardApplicationServiceImpl.class);
    private final AppSamlConfigConverter configConverter;

    public SamlStandardApplicationServiceImpl(AppCertRepository appCertRepository,
                                                 AppGroupAssociationRepository appGroupAssociationRepository,
                                                 AppAccountRepository appAccountRepository,
                                                 AppAccessPolicyRepository appAccessPolicyRepository,
                                                 AppRepository appRepository,
                                                 SamlConfigStore samlConfigStore,
                                                 AppSamlConfigConverter configConverter) {
        super(appCertRepository, appGroupAssociationRepository, appAccountRepository,
                appAccessPolicyRepository, appRepository, samlConfigStore);
        this.configConverter = configConverter;
    }

    @Override
    public String create(String name, String icon, String remark, List<String> groups) {
        // 1. Create the application.
        AppEntity appEntity = createApp(name, icon, remark, groups,
                AuthorizationType.AUTHORIZATION);
        // 2. Reuse the generic signing certificate slot until AppCertUsingType declares SAML.
        createCertificate(appEntity.getId(), appEntity.getCode(), AppCertUsingType.OIDC_JWK);
        // 3. Persist the default protocol configuration.
        SamlConfigData data = new SamlConfigData();
        data.setNameIdFormat(SamlNameIdFormat.PERSISTENT.getCode());
        data.setDefaultBinding(SamlBindingType.POST.getCode());
        data.setAssertionTimeToLiveSeconds(DEFAULT_ASSERTION_TIME_TO_LIVE_SECONDS);
        data.setClockSkewSeconds(DEFAULT_CLOCK_SKEW_SECONDS);
        data.setWantAssertionsSigned(true);
        data.setWantAuthnRequestsSigned(false);
        data.setAttributeMappings(Map.of());
        samlConfigStore.save(appEntity.getId(), data);
        return appEntity.getId();
    }

    @Override
    public void saveConfig(String appId, Map<String, Object> config) {
        AppSamlSaveConfigParam model;
        try {
            ObjectMapper mapper = new ObjectMapper();
            String value = mapper.writeValueAsString(config);
            mapper.configure(FAIL_ON_UNKNOWN_PROPERTIES, false);
            model = mapper.readValue(value, AppSamlSaveConfigParam.class);
        } catch (Exception exception) {
            throw new PudansException(exception.getMessage());
        }
        ValidationUtils.ValidationResult<AppSamlSaveConfigParam> validationResult =
                ValidationUtils.validateEntity(model);
        if (validationResult.isHasErrors()) {
            throw new ConstraintViolationException(validationResult.getConstraintViolations());
        }
        Optional<AppEntity> optional = appRepository.findById(appId);
        if (optional.isEmpty()) {
            AuditContext.setContent("SAML configuration save failed, application [" + appId
                    + "] does not exist");
            logger.error(AuditContext.getContent());
            throw new AppNotExistException();
        }
        AppEntity appEntity = optional.get();
        appEntity.setConfigured(true);
        appEntity.setInitLoginUrl(model.getInitLoginUrl());
        appRepository.save(appEntity);
        samlConfigStore.save(appId, configConverter.toConfigData(model));
    }

    @Override
    public Object getConfig(String appId) {
        AppEntity app = appRepository.findById(appId).orElseThrow(AppNotExistException::new);
        return configConverter.toGetResult(app, samlConfigStore.find(appId));
    }

    @Override
    public String getCode() {
        return "saml";
    }

    @Override
    public String getName() {
        return "SAML";
    }

    @Override
    public String getDescription() {
        return "SAML（Security Assertion Markup Language）是一种基于 XML 的开放标准，"
                + "用于在身份提供者和服务提供者之间交换认证与授权数据。PUDANS 作为身份提供者，"
                + "向 SAML 服务提供者签发断言，实现单点登录与单点登出。";
    }

    @Override
    public AppType getType() {
        return AppType.STANDARD;
    }

    @Override
    public AppProtocol getProtocol() {
        // The shared AppProtocol enum does not declare SAML yet.
        return null;
    }

    @Override
    public String getBase64Icon() {
        return ICON;
    }
}