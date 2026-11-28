package cn.tolim.pudans.iam.protocol.saml.converter;

import cn.tolim.pudans.iam.communal.entity.app.AppEntity;
import cn.tolim.pudans.iam.protocol.saml.constant.SamlBindingType;
import cn.tolim.pudans.iam.protocol.saml.constant.SamlNameIdFormat;
import cn.tolim.pudans.iam.protocol.saml.model.SamlConfigData;
import cn.tolim.pudans.iam.protocol.saml.pojo.AppSamlConfigGetResult;
import cn.tolim.pudans.iam.protocol.saml.pojo.AppSamlProtocolEndpoint;
import cn.tolim.pudans.iam.protocol.saml.pojo.AppSamlSaveConfigParam;
import cn.tolim.pudans.iam.protocol.saml.service.PortalSamlEndpointResolver;
import org.mapstruct.Mapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static cn.tolim.pudans.iam.protocol.saml.constant.SamlProtocolConstants.DEFAULT_ASSERTION_TIME_TO_LIVE_SECONDS;
import static cn.tolim.pudans.iam.protocol.saml.constant.SamlProtocolConstants.DEFAULT_CLOCK_SKEW_SECONDS;


@Mapper(componentModel = "spring")
public interface AppSamlConfigConverter {

    // Convert the save parameter to configuration data.
    default SamlConfigData toConfigData(AppSamlSaveConfigParam param) {
        if (param == null) {
            return null;
        }
        SamlConfigData data = new SamlConfigData();
        data.setEntityId(param.getEntityId());
        data.setAssertionConsumerServiceUrl(param.getAssertionConsumerServiceUrl());
        data.setSingleLogoutServiceUrl(param.getSingleLogoutServiceUrl());
        data.setNameIdFormat(param.getNameIdFormat() == null
                ? SamlNameIdFormat.PERSISTENT.getCode() : param.getNameIdFormat().getCode());
        data.setDefaultBinding(param.getDefaultBinding() == null
                ? SamlBindingType.POST.getCode() : param.getDefaultBinding().getCode());
        data.setAssertionTimeToLiveSeconds(param.getAssertionTimeToLive() == null
                ? DEFAULT_ASSERTION_TIME_TO_LIVE_SECONDS
                : param.getAssertionTimeToLive().longValue());
        data.setClockSkewSeconds(param.getClockSkew() == null
                ? DEFAULT_CLOCK_SKEW_SECONDS : param.getClockSkew().longValue());
        data.setWantAssertionsSigned(param.getWantAssertionsSigned());
        data.setWantAuthnRequestsSigned(param.getWantAuthnRequestsSigned());
        data.setSigningAlgorithm(param.getSigningAlgorithm());
        data.setAttributeMappings(param.getAttributeMappings() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(param.getAttributeMappings()));
        return data;
    }

    // Convert the application and configuration data to the get result.
    default AppSamlConfigGetResult toGetResult(AppEntity app, SamlConfigData data) {
        AppSamlConfigGetResult result = new AppSamlConfigGetResult();
        result.setAppId(app.getId());
        result.setInitLoginUrl(app.getInitLoginUrl());
        if (data != null) {
            result.setEntityId(data.getEntityId());
            result.setAssertionConsumerServiceUrl(data.getAssertionConsumerServiceUrl());
            result.setSingleLogoutServiceUrl(data.getSingleLogoutServiceUrl());
            result.setNameIdFormat(SamlNameIdFormat.getType(data.getNameIdFormat()));
            result.setDefaultBinding(SamlBindingType.getType(data.getDefaultBinding()));
            result.setAssertionTimeToLive(Objects.toString(
                    data.getAssertionTimeToLiveSeconds(), ""));
            result.setClockSkew(Objects.toString(data.getClockSkewSeconds(), ""));
            result.setWantAssertionsSigned(data.getWantAssertionsSigned());
            result.setWantAuthnRequestsSigned(data.getWantAuthnRequestsSigned());
            result.setSigningAlgorithm(data.getSigningAlgorithm());
            result.setAttributeMappings(data.getAttributeMappings() == null
                    ? Map.of() : data.getAttributeMappings());
        }
        result.setProtocolEndpoint(buildProtocolEndpoint(app.getCode()));
        return result;
    }

    // Build the protocol endpoint description.
    default AppSamlProtocolEndpoint buildProtocolEndpoint(String appCode) {
        AppSamlProtocolEndpoint endpoint = new AppSamlProtocolEndpoint();
        PortalSamlEndpointResolver resolver = new PortalSamlEndpointResolver();
        endpoint.setIdpEntityId(resolver.idpEntityId(appCode));
        endpoint.setMetadataEndpoint(resolver.metadataUrl(appCode));
        endpoint.setIdpSsoEndpoint(resolver.ssoUrl(appCode));
        endpoint.setIdpSloEndpoint(resolver.sloUrl(appCode));
        return endpoint;
    }
}