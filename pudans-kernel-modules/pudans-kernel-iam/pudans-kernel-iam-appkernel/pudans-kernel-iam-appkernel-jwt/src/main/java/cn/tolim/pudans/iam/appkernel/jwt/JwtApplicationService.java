package cn.tolim.pudans.iam.appkernel.jwt;

import cn.tolim.pudans.iam.appkernel.core.ApplicationService;
import cn.tolim.pudans.iam.appkernel.jwt.model.JwtProtocolConfig;


public interface JwtApplicationService extends ApplicationService {

    // Get protocol configuration
    JwtProtocolConfig getProtocolConfig(String appCode);
}
