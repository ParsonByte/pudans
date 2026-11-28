package cn.tolim.pudans.iam.protocol.cas.application;

import cn.tolim.pudans.iam.appkernel.core.ApplicationService;
import cn.tolim.pudans.iam.protocol.cas.model.CasProtocolConfig;


public interface CasApplicationService extends ApplicationService {

    // Get the CAS protocol configuration by application code.
    CasProtocolConfig getProtocolConfig(String appCode);
}