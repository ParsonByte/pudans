package cn.tolim.pudans.iam.protocol.saml.service;

import cn.tolim.pudans.iam.core.context.ContextService;
import cn.tolim.pudans.iam.protocol.saml.constant.SamlProtocolConstants.SamlEndpointConstants;
import org.apache.commons.text.StringSubstitutor;

import java.util.HashMap;
import java.util.Map;

import static cn.tolim.pudans.iam.communal.constant.ProtocolConstants.APP_CODE;


public class PortalSamlEndpointResolver implements SamlEndpointResolver {

    // Replace the application code placeholder in an endpoint path.
    private static String resolve(String appCode, String path) {
        Map<String, String> variables = new HashMap<>(16);
        variables.put(APP_CODE, appCode);
        StringSubstitutor substitutor = new StringSubstitutor(variables, "{", "}");
        return substitutor.replace(ContextService.getPortalPublicBaseUrl() + path);
    }

    @Override
    public String idpEntityId(String appCode) {
        return resolve(appCode, SamlEndpointConstants.SAML_AUTHORIZE_BASE_PATH);
    }

    @Override
    public String metadataUrl(String appCode) {
        return resolve(appCode, SamlEndpointConstants.SAML_METADATA_PATH);
    }

    @Override
    public String ssoUrl(String appCode) {
        return resolve(appCode, SamlEndpointConstants.SAML_SSO_PATH);
    }

    @Override
    public String sloUrl(String appCode) {
        return resolve(appCode, SamlEndpointConstants.SAML_SLO_PATH);
    }
}