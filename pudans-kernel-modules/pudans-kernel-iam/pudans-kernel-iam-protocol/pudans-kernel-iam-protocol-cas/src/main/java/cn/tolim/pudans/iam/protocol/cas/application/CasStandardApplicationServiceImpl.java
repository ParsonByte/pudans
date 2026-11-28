package cn.tolim.pudans.iam.protocol.cas.application;

import cn.tolim.pudans.audit.context.AuditContext;
import cn.tolim.pudans.iam.appkernel.core.exception.AppNotExistException;
import cn.tolim.pudans.iam.communal.entity.app.AppEntity;
import cn.tolim.pudans.iam.communal.enums.app.AppProtocol;
import cn.tolim.pudans.iam.communal.enums.app.AppType;
import cn.tolim.pudans.iam.communal.enums.app.AuthorizationType;
import cn.tolim.pudans.iam.communal.repository.app.AppAccessPolicyRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppAccountRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppGroupAssociationRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppRepository;
import cn.tolim.pudans.iam.infra.exception.PudansException;
import cn.tolim.pudans.iam.infra.validation.ValidationUtils;
import cn.tolim.pudans.iam.protocol.cas.converter.AppCasConfigConverter;
import cn.tolim.pudans.iam.protocol.cas.model.CasConfigData;
import cn.tolim.pudans.iam.protocol.cas.pojo.AppCasSaveConfigParam;
import cn.tolim.pudans.iam.protocol.cas.store.CasConfigStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cn.tolim.pudans.iam.protocol.cas.constant.CasProtocolConstants.DEFAULT_COOKIE_NAME;
import static cn.tolim.pudans.iam.protocol.cas.constant.CasProtocolConstants.DEFAULT_TICKET_GRANTING_TICKET_TIME_TO_LIVE_SECONDS;
import static cn.tolim.pudans.iam.protocol.cas.constant.CasProtocolConstants.DEFAULT_TICKET_TIME_TO_LIVE_SECONDS;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;


@Component
public class CasStandardApplicationServiceImpl extends AbstractCasApplicationService {

    private static final String ICON = "data:image/svg+xml;base64,"
            + "PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI2NCIgaGVpZ2h0PSI2NCIg"
            + "dmlld0JveD0iMCAwIDY0IDY0Ij48cmVjdCB3aWR0aD0iNjQiIGhlaWdodD0iNjQiIHJ4PSIxMiIgZmls"
            + "bD0iIzBmOWQ1OCIvPjx0ZXh0IHg9IjMyIiB5PSI0MCIgZm9udC1mYW1pbHk9IkFyaWFsIiBmb250LXNp"
            + "emU9IjIwIiBmb250LXdlaWdodD0iYm9sZCIgZmlsbD0iI2ZmZmZmZiIgdGV4dC1hbmNob3I9Im1pZGRs"
            + "ZSI+Q0FTPC90ZXh0Pjwvc3ZnPg==";

    private final Logger logger = LoggerFactory.getLogger(CasStandardApplicationServiceImpl.class);
    private final AppCasConfigConverter configConverter;

    public CasStandardApplicationServiceImpl(AppAccountRepository appAccountRepository,
                                                AppGroupAssociationRepository appGroupAssociationRepository,
                                                AppRepository appRepository,
                                                AppAccessPolicyRepository appAccessPolicyRepository,
                                                CasConfigStore casConfigStore,
                                                AppCasConfigConverter configConverter) {
        super(appAccountRepository, appGroupAssociationRepository, appRepository,
                appAccessPolicyRepository, casConfigStore);
        this.configConverter = configConverter;
    }

    @Override
    public String create(String name, String icon, String remark, List<String> groups) {
        AppEntity appEntity = createApp(name, icon, remark, groups,
                AuthorizationType.AUTHORIZATION);
        CasConfigData data = new CasConfigData();
        data.setServiceUrls(new LinkedHashSet<>());
        data.setTicketTimeToLiveSeconds(DEFAULT_TICKET_TIME_TO_LIVE_SECONDS);
        data.setTicketGrantingTicketTimeToLiveSeconds(
                DEFAULT_TICKET_GRANTING_TICKET_TIME_TO_LIVE_SECONDS);
        data.setRenewEnabled(false);
        data.setSecureCookie(true);
        data.setCookieName(DEFAULT_COOKIE_NAME);
        data.setAttributeMappings(new LinkedHashMap<>());
        casConfigStore.save(appEntity.getId(), data);
        return appEntity.getId();
    }

    @Override
    public void saveConfig(String appId, Map<String, Object> config) {
        AppCasSaveConfigParam model;
        try {
            ObjectMapper mapper = new ObjectMapper();
            String value = mapper.writeValueAsString(config);
            mapper.configure(FAIL_ON_UNKNOWN_PROPERTIES, false);
            model = mapper.readValue(value, AppCasSaveConfigParam.class);
        } catch (Exception exception) {
            throw new PudansException(exception.getMessage());
        }
        ValidationUtils.ValidationResult<AppCasSaveConfigParam> validationResult =
                ValidationUtils.validateEntity(model);
        if (validationResult.isHasErrors()) {
            throw new ConstraintViolationException(validationResult.getConstraintViolations());
        }
        Optional<AppEntity> optional = appRepository.findById(appId);
        if (optional.isEmpty()) {
            AuditContext.setContent("CAS configuration save failed, application [" + appId
                    + "] does not exist");
            logger.error(AuditContext.getContent());
            throw new AppNotExistException();
        }
        AppEntity appEntity = optional.get();
        appEntity.setConfigured(true);
        appEntity.setInitLoginUrl(model.getInitLoginUrl());
        appRepository.save(appEntity);
        casConfigStore.save(appId, configConverter.toConfigData(model));
    }

    @Override
    public Object getConfig(String appId) {
        AppEntity app = appRepository.findById(appId).orElseThrow(AppNotExistException::new);
        return configConverter.toGetResult(app, casConfigStore.find(appId));
    }

    @Override
    public String getCode() {
        return "cas";
    }

    @Override
    public String getName() {
        return "CAS";
    }

    @Override
    public String getDescription() {
        return "CAS（Central Authentication Service）是一种面向 Web 的单点登录协议，"
                + "通过票据（TGT、ST）在身份提供者与服务之间建立信任。PUDANS 作为 CAS 服务端，"
                + "为接入应用提供登录、票据签发、服务票据校验与登出能力。";
    }

    @Override
    public AppType getType() {
        return AppType.STANDARD;
    }

    @Override
    public AppProtocol getProtocol() {
        // The shared AppProtocol enum does not declare CAS yet.
        return null;
    }

    @Override
    public String getBase64Icon() {
        return ICON;
    }
}