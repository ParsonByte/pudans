package cn.tolim.pudans.iam.communal.entity.app.po;

import cn.tolim.pudans.iam.communal.entity.app.AppFormConfigEntity;
import cn.tolim.pudans.iam.communal.enums.app.AuthorizationType;
import lombok.Data;
import lombok.EqualsAndHashCode;


@Data
@EqualsAndHashCode(callSuper = true)
public class AppFormConfigPO extends AppFormConfigEntity {

    // Application code
    private String appCode;

    // Application name
    private String appName;

    // Template
    private String appTemplate;

    // Client ID
    private String clientId;

    // Client secret
    private String clientSecret;

    // SSO login link
    private String initLoginUrl;

    // Scope
    private AuthorizationType authorizationType;

    // Application enabled
    private Boolean enabled;

    // Configured
    private Boolean configured;

    public AppFormConfigPO(AppFormConfigEntity config, String appCode, String appName,
                           String appTemplate, String clientId, String clientSecret,
                           String initLoginUrl, AuthorizationType authorizationType,
                           Boolean enabled, Boolean configured) {
        // DTO field
        this.appCode = appCode;
        this.appName = appName;
        this.appTemplate = appTemplate;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.initLoginUrl = initLoginUrl;
        this.authorizationType = authorizationType;
        this.enabled = enabled;
        this.configured = configured;
        // FORM configuration field
        super.setAppId(config.getAppId());
        super.setLoginUrl(config.getLoginUrl());
        super.setUsernameField(config.getUsernameField());
        super.setUsernameEncryptKey(config.getUsernameEncryptKey());
        super.setUsernameEncryptType(config.getUsernameEncryptType());
        super.setPasswordField(config.getPasswordField());
        super.setPasswordEncryptKey(config.getPasswordEncryptKey());
        super.setPasswordEncryptType(config.getPasswordEncryptType());
        super.setSubmitType(config.getSubmitType());
        super.setOtherField(config.getOtherField());

        super.setCreateBy(config.getCreateBy());
        super.setCreateTime(config.getCreateTime());
        super.setUpdateBy(config.getUpdateBy());
        super.setUpdateTime(config.getUpdateTime());
        super.setRemark(config.getRemark());
    }
}
