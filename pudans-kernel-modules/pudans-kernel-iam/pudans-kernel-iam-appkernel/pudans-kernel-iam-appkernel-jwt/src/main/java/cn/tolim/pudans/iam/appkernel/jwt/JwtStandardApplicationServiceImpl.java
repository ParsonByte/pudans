package cn.tolim.pudans.iam.appkernel.jwt;

import cn.tolim.pudans.audit.context.AuditContext;
import cn.tolim.pudans.iam.appkernel.core.exception.AppNotExistException;
import cn.tolim.pudans.iam.appkernel.jwt.converter.AppJwtConfigConverter;
import cn.tolim.pudans.iam.appkernel.jwt.pojo.AppJwtSaveConfigParam;
import cn.tolim.pudans.iam.communal.entity.app.AppEntity;
import cn.tolim.pudans.iam.communal.entity.app.AppJwtConfigEntity;
import cn.tolim.pudans.iam.communal.entity.app.po.AppJwtConfigPO;
import cn.tolim.pudans.iam.communal.enums.app.*;
import cn.tolim.pudans.iam.communal.repository.app.*;
import cn.tolim.pudans.iam.infra.exception.PudansException;
import cn.tolim.pudans.iam.infra.util.BeanUtils;
import cn.tolim.pudans.iam.infra.validation.ValidationUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cn.tolim.pudans.iam.infra.repository.base.BaseEntity.LAST_MODIFIED_BY;
import static cn.tolim.pudans.iam.infra.repository.base.BaseEntity.LAST_MODIFIED_TIME;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;


@Component
@Slf4j
public class JwtStandardApplicationServiceImpl extends AbstractJwtApplicationService {

    private final AppJwtConfigConverter appJwtConfigConverter;

    public JwtStandardApplicationServiceImpl(AppJwtConfigRepository appJwtConfigRepository,
                                             AppGroupAssociationRepository appGroupAssociationRepository,
                                             AppJwtConfigConverter appJwtConfigConverter,
                                             AppCertRepository appCertRepository,
                                             AppRepository appRepository,
                                             AppAccountRepository appAccountRepository,
                                             AppAccessPolicyRepository appAccessPolicyRepository) {
        super(appJwtConfigRepository, appGroupAssociationRepository, appCertRepository,
                appRepository, appAccountRepository, appAccessPolicyRepository);
        this.appJwtConfigConverter = appJwtConfigConverter;
    }

    // Update application configuration
    @Override
    public void saveConfig(String appId, Map<String, Object> config) {
        AppJwtSaveConfigParam model;
        try {
            ObjectMapper mapper = new ObjectMapper();
            String value = mapper.writeValueAsString(config);
            // Specify the serialized input type
            mapper.configure(FAIL_ON_UNKNOWN_PROPERTIES, false);
            model = mapper.readValue(value, AppJwtSaveConfigParam.class);
        } catch (Exception e) {
            throw new PudansException(e.getMessage());
        }
        ValidationUtils.ValidationResult<AppJwtSaveConfigParam> validationResult = ValidationUtils.validateEntity(model);
        if (validationResult.isHasErrors()) {
            throw new ConstraintViolationException(validationResult.getConstraintViolations());
        }
        // 1. Modify basic information
        Optional<AppEntity> optional = appRepository.findById(appId);
        if (optional.isEmpty()) {
            AuditContext.setContent("保存配置失败，应用 [" + appId + "] 不存在！");
            log.error(AuditContext.getContent());
            throw new AppNotExistException();
        }
        AppEntity appEntity = optional.get();
        appEntity.setInitLoginUrl(model.getInitLoginUrl());
        appEntity.setConfigured(true);
        appRepository.save(appEntity);
        // 2. Modify JWT configuration
        Optional<AppJwtConfigEntity> jwt = appJwtConfigRepository.findByAppId(appId);
        if (jwt.isEmpty()) {
            AuditContext.setContent("保存配置失败，应用 [" + appId + "] 不存在！");
            log.error(AuditContext.getContent());
            throw new AppNotExistException();
        }
        AppJwtConfigEntity entity = jwt.get();
        AppJwtConfigEntity formConfig = appJwtConfigConverter.appJwtSaveConfigParamToEntity(model);
        BeanUtils.merge(formConfig, entity, LAST_MODIFIED_BY, LAST_MODIFIED_TIME);
        appJwtConfigRepository.save(entity);
    }

    // Get configuration
    @Override
    public Object getConfig(String appId) {
        AppJwtConfigPO configPo = appJwtConfigRepository.getByAppId(appId);
        return appJwtConfigConverter.entityConverterToFormConfigResult(configPo);
    }

    // Get application identifier
    @Override
    public String getCode() {
        return AppProtocol.JWT.getCode();
    }

    // Get application name
    @Override
    public String getName() {
        return AppProtocol.JWT.getDesc();
    }

    // Get application description
    @Override
    public String getDescription() {
        return "JWT（JSON Web Token）是在网络应用环境声明的一种基于 JSON 的开放标准。PUDANS 使用 JWT 进行分布式站点的单点登录 （SSO）。JWT 单点登录基于非对称加密，由 PUDANS 将用户状态和信息使用私钥加密，传递给应用后，应用使用公钥解密并进行验证。使用场景非常广泛，集成简单。";
    }

    // Get application type
    @Override
    public AppType getType() {
        return AppType.STANDARD;
    }

    // Get application protocol
    @Override
    public AppProtocol getProtocol() {
        return AppProtocol.JWT;
    }

    // Get base64 icon
    @Override
    public String getBase64Icon() {
        return "data:image/svg+xml;base64,PHN2ZyB0PSIxNjYwOTc4MjExNDg3IiBjbGFzcz0iaWNvbiIgdmlld0JveD0iMCAwIDEwMjQgMTAyNCIgdmVyc2lvbj0iMS4xIiB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHAtaWQ9IjM4NzEiIHdpZHRoPSIyMDAiIGhlaWdodD0iMjAwIj48cGF0aCBkPSJNNTg5LjMxMiAyNzUuNDU2TDU4OC4yODggMGgtMTUzLjZsMS4wMjQgMjc1LjQ1NiA3Ni44IDEwNS40NzJ6IG0tMTUzLjYgNDcyLjA2NHYyNzYuNDhoMTUzLjZ2LTI3Ni40OEw1MTIuNTEyIDY0Mi4wNDh6IiBmaWxsPSIjRkZGRkZGIiBwLWlkPSIzODcyIj48L3BhdGg+PHBhdGggZD0iTTU4OS4zMTIgNzQ3LjUybDE2MS43OTIgMjIzLjIzMiAxMjMuOTA0LTkwLjExMi0xNjEuNzkyLTIyMy4yMzItMTIzLjkwNC0zOS45MzZ6IG0tMTUzLjYtNDcyLjA2NEwyNzIuODk2IDUyLjIyNGwtMTIzLjkwNCA5MC4xMTJMMzEwLjc4NCAzNjUuNTY4bDEyNC45MjggMzkuOTM2eiIgZmlsbD0iIzAwRjJFNiIgcC1pZD0iMzg3MyI+PC9wYXRoPjxwYXRoIGQ9Ik0zMTAuNzg0IDM2NS41NjhMNDguNjQgMjgwLjU3NiAxLjUzNiA0MjUuOTg0IDI2My42OCA1MTJsMTIzLjkwNC00MC45NnogbTMyNS42MzIgMTg2LjM2OGw3Ni44IDEwNS40NzIgMjYyLjE0NCA4NC45OTIgNDcuMTA0LTE0NS40MDgtMjYyLjE0NC04NC45OTJ6IiBmaWxsPSIjMDBCOUYxIiBwLWlkPSIzODc0Ij48L3BhdGg+PHBhdGggZD0iTTc2MC4zMiA1MTJsMjYyLjE0NC04Ni4wMTYtNDcuMTA0LTE0NS40MDhMNzEzLjIxNiAzNjUuNTY4bC03Ni44IDEwNS40NzJ6IG0tNDk2LjY0IDBMMS41MzYgNTk2Ljk5MiA0OC42NCA3NDIuNGwyNjIuMTQ0LTg0Ljk5MiA3Ni44LTEwNS40NzJ6IiBmaWxsPSIjRDYzQUZGIiBwLWlkPSIzODc1Ij48L3BhdGg+PHBhdGggZD0iTTMxMC43ODQgNjU3LjQwOEwxNDguOTkyIDg4MC42NGwxMjMuOTA0IDkwLjExMiAxNjIuODE2LTIyMy4yMzJWNjE3LjQ3MnpNNzEzLjIxNiAzNjUuNTY4bDE2MS43OTItMjIzLjIzMi0xMjMuOTA0LTkwLjExMi0xNjEuNzkyIDIyMy4yMzJ2MTMwLjA0OHoiIGZpbGw9IiNGQjAxNUIiIHAtaWQ9IjM4NzYiPjwvcGF0aD48L3N2Zz4=";
    }

    // Create application
    @Override
    public String create(String name, String icon, String remark, List<String> groups) {
        // 1. Create application
        AppEntity appEntity = createApp(name, icon, remark, groups,
                AuthorizationType.AUTHORIZATION);
        // JWT configuration
        AppJwtConfigEntity jwtConfigEntity = new AppJwtConfigEntity();
        jwtConfigEntity.setAppId(appEntity.getId());
        jwtConfigEntity.setBindingType(JwtBindingType.POST);
        // id_token sub type
        jwtConfigEntity.setIdTokenSubjectType(JwtIdTokenSubjectType.USER_ID);
        // Token lifetime (seconds)
        jwtConfigEntity.setIdTokenTimeToLive(Duration.ofSeconds(600));
        appJwtConfigRepository.save(jwtConfigEntity);
        // Create RSA certificate
        createCertificate(appEntity.getId(), appEntity.getCode(), AppCertUsingType.JWT_ENCRYPT);
        return String.valueOf(appEntity.getId());
    }
}
