package cn.tolim.pudans.iam.appkernel.jwt.converter;

import cn.tolim.pudans.iam.appkernel.jwt.pojo.AppJwtConfigGetResult;
import cn.tolim.pudans.iam.appkernel.jwt.pojo.AppJwtProtocolEndpoint;
import cn.tolim.pudans.iam.appkernel.jwt.pojo.AppJwtSaveConfigParam;
import cn.tolim.pudans.iam.communal.entity.app.AppJwtConfigEntity;
import cn.tolim.pudans.iam.communal.entity.app.po.AppJwtConfigPO;
import cn.tolim.pudans.iam.core.context.ContextService;
import org.apache.commons.text.StringSubstitutor;
import org.mapstruct.Mapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static cn.tolim.pudans.iam.communal.constant.ProtocolConstants.APP_CODE;
import static cn.tolim.pudans.iam.communal.constant.ProtocolConstants.JwtEndpointConstants.JWT_SSO_PATH;


@Mapper(componentModel = "spring")
public interface AppJwtConfigConverter {

    // Convert save model to entity
    default AppJwtConfigEntity appJwtSaveConfigParamToEntity(AppJwtSaveConfigParam config) {
        if (config == null) {
            return null;
        }

        AppJwtConfigEntity entity = new AppJwtConfigEntity();
        entity.setRedirectUrl(config.getRedirectUrl());
        entity.setTargetLinkUrl(config.getTargetLinkUrl());
        entity.setBindingType(config.getBindingType());
        entity.setIdTokenSubjectType(config.getIdTokenSubjectType());
        entity.setIdTokenTimeToLive(Duration.ofSeconds(config.getIdTokenTimeToLive()));
        return entity;
    }

    // Convert PO to result
    default AppJwtConfigGetResult entityConverterToFormConfigResult(AppJwtConfigPO po) {
        if (po == null) {
            return null;
        }
        AppJwtConfigGetResult result = new AppJwtConfigGetResult();
        if (po.getAppId() != null) {
            result.setAppId(po.getAppId());
        }
        result.setInitLoginUrl(po.getInitLoginUrl());
        result.setRedirectUrl(po.getRedirectUrl());
        result.setTargetLinkUrl(po.getTargetLinkUrl());
        result.setBindingType(po.getBindingType());
        result.setIdTokenSubjectType(po.getIdTokenSubjectType());
        result.setIdTokenTimeToLive(Objects.toString(po.getIdTokenTimeToLive().toSeconds(), ""));
        result.setProtocolEndpoint(getProtocolEndpointDomain(po.getAppCode()));
        return result;
    }

    // Get protocol endpoint
    private AppJwtProtocolEndpoint getProtocolEndpointDomain(String appCode) {
        AppJwtProtocolEndpoint domain = new AppJwtProtocolEndpoint();
        Map<String,String> variables = new HashMap<>(16);
        variables.put(APP_CODE,appCode);
        StringSubstitutor sub = new StringSubstitutor(variables, "{", "}");
        // IdP SSO endpoint
        domain.setIdpSsoEndpoint(sub.replace(ContextService.getPortalPublicBaseUrl()+JWT_SSO_PATH));
        return domain;
    }
}
