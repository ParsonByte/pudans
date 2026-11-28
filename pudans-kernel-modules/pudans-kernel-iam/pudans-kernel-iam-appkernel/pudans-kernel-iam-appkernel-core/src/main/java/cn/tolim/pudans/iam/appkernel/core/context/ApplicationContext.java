package cn.tolim.pudans.iam.appkernel.core.context;

import java.util.Map;


public interface ApplicationContext {
    // Get application ID
    String getAppId();

    // Get client ID
    String getClientId();

    // Get application code
    String getAppCode();

    // Get application template
    String getAppTemplate();

    // Get protocol configuration
    Map<String, Object> getConfig();
}
