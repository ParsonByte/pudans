package cn.tolim.pudans.iam.communal.entity.app.po;

import cn.tolim.pudans.iam.communal.entity.app.AppAccountEntity;
import cn.tolim.pudans.iam.communal.enums.app.AppProtocol;
import cn.tolim.pudans.iam.communal.enums.app.AppType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;


@EqualsAndHashCode(callSuper = true)
@Data
public class AppAccountPO extends AppAccountEntity {

    // User name
    private String username;

    // Application name
    private String appName;

    // Template
    private String appTemplate;

    // Protocol
    private AppProtocol appProtocol;

    // Application type
    private AppType appType;

    public AppAccountPO(String id, String appId, String userId, String account,
                        LocalDateTime createTime, Boolean defaulted, String username,
                        String appName, AppType appType, String appTemplate,
                        AppProtocol appProtocol) {

        super.setId(id);
        super.setAppId(appId);
        super.setUserId(userId);
        super.setAccount(account);
        super.setCreateTime(createTime);
        super.setDefaulted(defaulted);
        this.username = username;
        this.appName = appName;
        this.appType = appType;
        this.appTemplate = appTemplate;
        this.appProtocol = appProtocol;
    }

    public AppAccountPO(String id, String appId, String userId, String username, String appName) {
        super.setId(id);
        super.setAppId(appId);
        super.setUserId(userId);
        this.username = username;
        this.appName = appName;
    }
}
