package cn.tolim.pudans.iam.protocol.saml.application;

import cn.tolim.pudans.iam.appkernel.core.ApplicationService;
import cn.tolim.pudans.iam.protocol.saml.model.SamlProtocolConfig;


public interface SamlApplicationService extends ApplicationService {

    // Get the SAML protocol configuration by application code.
    SamlProtocolConfig getProtocolConfig(String appCode);
}