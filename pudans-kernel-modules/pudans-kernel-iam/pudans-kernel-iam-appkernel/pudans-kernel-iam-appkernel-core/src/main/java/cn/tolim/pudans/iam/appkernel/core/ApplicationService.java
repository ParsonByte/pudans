package cn.tolim.pudans.iam.appkernel.core;

import cn.tolim.pudans.iam.communal.entity.app.AppEntity;
import cn.tolim.pudans.iam.communal.enums.app.AppProtocol;
import cn.tolim.pudans.iam.communal.enums.app.AppType;
import cn.tolim.pudans.iam.communal.enums.app.AuthorizationType;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public interface ApplicationService {

    // Get application identifier
    String getCode();

    // Get application name
    String getName();

    // Get application description
    String getDescription();

    // Get application type
    AppType getType();

    // Get application protocol
    AppProtocol getProtocol();

    // Get form schema
    default Object getFormSchema() {
        return null;
    }

    // Get base64 icon
    String getBase64Icon();

    // Create application
    @Transactional(rollbackFor = Exception.class)
    default String create(String name, String icon, String remark) {
        return create(name, icon, remark, new ArrayList<>());
    }

    // Create application
    @Transactional(rollbackFor = Exception.class)
    String create(String name, String icon, String remark, List<String> groups);

    // Delete application
    void delete(String appId);

    // Update application configuration
    @Transactional(rollbackFor = Exception.class)
    void saveConfig(String appId, Map<String, Object> config);

    // Get configuration
    Object getConfig(String appId);

    // Get default application user information
    AppAccount getDefaultAppAccount(String appId, String userId);

    // Create application
    AppEntity createApp(String name, String icon, String remark,
                        AuthorizationType authorizationType);

    // Create application
    AppEntity createApp(String name, String icon, String remark, List<String> groups,
                        AuthorizationType authorizationType);
}
