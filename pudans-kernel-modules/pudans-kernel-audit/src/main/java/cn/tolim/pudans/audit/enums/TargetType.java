package cn.tolim.pudans.audit.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;

import lombok.Getter;

@Getter
public enum TargetType {
    USER("user", "用户"),
    USER_DETAIL("user_detail", "用户详情"),
    USER_GROUP("user_group", "用户组"),
    USER_GROUP_MEMBER("user_group_member", "用户组成员"),
    IDENTITY_SOURCE("identity_source", "身份源"),
    ORGANIZATION("organization", "组织机构"),
    APPLICATION("application", "应用"),
    APPLICATION_ACCOUNT("application_account", "应用账户"),
    SESSION("session", "会话管理"),
    APP_PERMISSION_RESOURCE("app_permission_resource", "应用权限"),
    APP_PERMISSION_POLICY("app_permission_policy", "应用权限策略"),
    APP_PERMISSION_ROLE("app_permission_role", "应用权限角色"),
    APP_GROUP("app_group", "应用分组"),
    ROLE("role", "系统角色"),
    AUTHZ_ROLE("authz_role", "授权角色"),
    POLICY("policy", "授权策略"),
    POLICY_ATTACHMENT("policy_attachment", "策略绑定"),
    SERVICE_ACTION("service_action", "服务动作"),
    RESOURCE_TYPE("resource_type", "资源类型"),
    LOGIN_SESSION("login_session", "登录会话"),
    APP_SESSION("app_session", "应用会话"),
    SSO_TICKET("sso_ticket", "SSO票据"),
    ADMINISTRATOR("administrator", "管理员"),
    PASSWORD_POLICY("password_policy", "密码策略"),
    MAIL_TEMPLATE("mail_template", "邮件模版"),
    IDENTITY_PROVIDER("identity_provider", "身份认证提供商"),
    CONSOLE("console", "控制台"),
    PORTAL("portal", "门户端"),
    USER_IDP("user_idp", "用户绑定三方用户");

    @JsonValue
    private final String code;
    private final String desc;

    TargetType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @EnumConvert
    public static TargetType getType(String code) {
        TargetType[] values = values();
        for (TargetType status : values) {
            if (String.valueOf(status.getCode()).equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的目标类型");
    }

}
