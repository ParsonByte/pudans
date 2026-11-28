package cn.tolim.pudans.iam.appkernel.oidc;

import cn.tolim.pudans.iam.appkernel.core.ApplicationService;
import cn.tolim.pudans.iam.appkernel.oidc.model.OidcProtocolConfig;


public interface OidcApplicationService extends ApplicationService {

    // Get protocol configuration
    OidcProtocolConfig getProtocolConfig(String appCode);
}
