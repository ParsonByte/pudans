package cn.tolim.pudans.iam.protocol.cas.converter;

import cn.tolim.pudans.iam.communal.entity.app.AppEntity;
import cn.tolim.pudans.iam.core.context.ContextService;
import cn.tolim.pudans.iam.protocol.cas.constant.CasProtocolConstants.CasEndpointConstants;
import cn.tolim.pudans.iam.protocol.cas.model.CasConfigData;
import cn.tolim.pudans.iam.protocol.cas.pojo.AppCasConfigGetResult;
import cn.tolim.pudans.iam.protocol.cas.pojo.AppCasProtocolEndpoint;
import cn.tolim.pudans.iam.protocol.cas.pojo.AppCasSaveConfigParam;
import org.apache.commons.text.StringSubstitutor;
import org.mapstruct.Mapper;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;

import static cn.tolim.pudans.iam.communal.constant.ProtocolConstants.APP_CODE;
import static cn.tolim.pudans.iam.protocol.cas.constant.CasProtocolConstants.DEFAULT_COOKIE_NAME;
import static cn.tolim.pudans.iam.protocol.cas.constant.CasProtocolConstants.DEFAULT_TICKET_GRANTING_TICKET_TIME_TO_LIVE_SECONDS;
import static cn.tolim.pudans.iam.protocol.cas.constant.CasProtocolConstants.DEFAULT_TICKET_TIME_TO_LIVE_SECONDS;


@Mapper(componentModel = "spring")
public interface AppCasConfigConverter {

    // Convert the save parameter to configuration data.
    default CasConfigData toConfigData(AppCasSaveConfigParam param) {
        if (param == null) {
            return null;
        }
        CasConfigData data = new CasConfigData();
        data.setServiceUrls(param.getServiceUrls() == null
                ? new LinkedHashSet<>() : new LinkedHashSet<>(param.getServiceUrls()));
        data.setDefaultServiceUrl(param.getDefaultServiceUrl());
        data.setTicketTimeToLiveSeconds(param.getTicketTimeToLive() == null
                ? DEFAULT_TICKET_TIME_TO_LIVE_SECONDS : param.getTicketTimeToLive().longValue());
        data.setTicketGrantingTicketTimeToLiveSeconds(
                param.getTicketGrantingTicketTimeToLive() == null
                        ? DEFAULT_TICKET_GRANTING_TICKET_TIME_TO_LIVE_SECONDS
                        : param.getTicketGrantingTicketTimeToLive().longValue());
        data.setRenewEnabled(param.getRenewEnabled());
        data.setSecureCookie(param.getSecureCookie());
        data.setCookieName(param.getCookieName() == null
                ? DEFAULT_COOKIE_NAME : param.getCookieName());
        data.setAttributeMappings(param.getAttributeMappings() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(param.getAttributeMappings()));
        return data;
    }

    // Convert the application and configuration data to the get result.
    default AppCasConfigGetResult toGetResult(AppEntity app, CasConfigData data) {
        AppCasConfigGetResult result = new AppCasConfigGetResult();
        result.setAppId(app.getId());
        result.setInitLoginUrl(app.getInitLoginUrl());
        if (data != null) {
            result.setServiceUrls(data.getServiceUrls() == null
                    ? java.util.Set.of() : data.getServiceUrls());
            result.setDefaultServiceUrl(data.getDefaultServiceUrl());
            result.setTicketTimeToLive(Objects.toString(data.getTicketTimeToLiveSeconds(), ""));
            result.setTicketGrantingTicketTimeToLive(Objects.toString(
                    data.getTicketGrantingTicketTimeToLiveSeconds(), ""));
            result.setRenewEnabled(data.getRenewEnabled());
            result.setSecureCookie(data.getSecureCookie());
            result.setCookieName(data.getCookieName());
            result.setAttributeMappings(data.getAttributeMappings() == null
                    ? Map.of() : data.getAttributeMappings());
        }
        result.setProtocolEndpoint(buildProtocolEndpoint(app.getCode()));
        return result;
    }

    // Build the protocol endpoint description.
    default AppCasProtocolEndpoint buildProtocolEndpoint(String appCode) {
        AppCasProtocolEndpoint endpoint = new AppCasProtocolEndpoint();
        Map<String, String> variables = new HashMap<>(16);
        variables.put(APP_CODE, appCode);
        StringSubstitutor substitutor = new StringSubstitutor(variables, "{", "}");
        String baseUrl = ContextService.getPortalPublicBaseUrl();
        endpoint.setLoginEndpoint(substitutor.replace(baseUrl + CasEndpointConstants.CAS_LOGIN_PATH));
        endpoint.setLogoutEndpoint(substitutor.replace(
                baseUrl + CasEndpointConstants.CAS_LOGOUT_PATH));
        endpoint.setServiceValidateEndpoint(substitutor.replace(
                baseUrl + CasEndpointConstants.CAS_SERVICE_VALIDATE_PATH));
        endpoint.setP3ServiceValidateEndpoint(substitutor.replace(
                baseUrl + CasEndpointConstants.CAS_P3_SERVICE_VALIDATE_PATH));
        endpoint.setValidateEndpoint(substitutor.replace(
                baseUrl + CasEndpointConstants.CAS_VALIDATE_PATH));
        return endpoint;
    }
}