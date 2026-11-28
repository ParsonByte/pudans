package cn.tolim.pudans.iam.communal.entity.app.po;

import cn.tolim.pudans.iam.communal.entity.app.AppJwtConfigEntity;
import cn.tolim.pudans.iam.communal.enums.app.AuthorizationType;
import lombok.Data;
import lombok.EqualsAndHashCode;


@Data
@EqualsAndHashCode(callSuper = true)
public class AppJwtConfigPO extends AppJwtConfigEntity {

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

    public AppJwtConfigPO(AppJwtConfigEntity config, String appCode, String appName,
                          String appTemplate, String clientId, String clientSecret,
                          String initLoginUrl, AuthorizationType authorizationType, Boolean enabled,
                          Boolean configured) {
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
        // JWT configuration field
        super.setAppId(config.getAppId());
        super.setRedirectUrl(config.getRedirectUrl());
        super.setTargetLinkUrl(config.getTargetLinkUrl());
        super.setBindingType(config.getBindingType());
        super.setIdTokenSubjectType(config.getIdTokenSubjectType());
        super.setIdTokenTimeToLive(config.getIdTokenTimeToLive());

        super.setCreateBy(config.getCreateBy());
        super.setCreateTime(config.getCreateTime());
        super.setUpdateBy(config.getUpdateBy());
        super.setUpdateTime(config.getUpdateTime());
        super.setRemark(config.getRemark());
    }
}
