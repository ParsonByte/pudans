package cn.tolim.pudans.iam.communal.repository.app;

import cn.tolim.pudans.iam.communal.entity.app.po.AppFormConfigPO;


public interface AppFormConfigRepositoryCustomized {
    // Get by application ID
    // Parameter appId: Long
    // Returns: AppFormConfigPO
    AppFormConfigPO getByAppId(Long appId);

    // Get by application client
    // Parameter clientId: String
    // Returns: AppFormConfigPO
    AppFormConfigPO getByClientId(String clientId);

    // Query application configuration by application code
    // Parameter appCode: String
    // Returns: AppFormConfigPO
    AppFormConfigPO findByAppCode(String appCode);
}
