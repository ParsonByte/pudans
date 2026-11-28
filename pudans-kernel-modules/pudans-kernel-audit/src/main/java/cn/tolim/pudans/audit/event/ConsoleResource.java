package cn.tolim.pudans.audit.event;

public class ConsoleResource extends Resource {

    public static final ConsoleResource ORG_ACCOUNT_RESOURCE = new ConsoleResource(
            "pudans:event:resource:org_account", "组织与用户");

    public static final ConsoleResource USER_GROUP_RESOURCE = new ConsoleResource(
            "pudans:event:resource:user_group", "用户组管理");

    public static final ConsoleResource IDENTITY_SOURCE_RESOURCE = new ConsoleResource(
            "pudans:event:resource:identity_source", "身份源管理");

    public static final ConsoleResource AUTHENTICATION_RESOURCE = new ConsoleResource(
            "pudans:event:resource:authentication", "认证管理");

    public static final ConsoleResource IDP_RESOURCE = new ConsoleResource(
            "pudans:event:resource:idp", "身份提供商");

    public static final ConsoleResource APP_RESOURCE = new ConsoleResource(
            "pudans:event:resource:application", "应用管理");

    public static final ConsoleResource SECURITY_RESOURCE = new ConsoleResource(
            "pudans:event:resource:security", "通用安全");

    public static final ConsoleResource PASSWORD_POLICY_RESOURCE = new ConsoleResource(
            "pudans:event:resource:password_policy", "密码策略");

    public static final ConsoleResource ADMINISTRATOR_RESOURCE = new ConsoleResource(
            "pudans:event:resource:administrator", "系统管理员");

    public static final ConsoleResource MESSAGE_RESOURCE = new ConsoleResource(
            "pudans:event:resource:message", "消息设置");

    public static final ConsoleResource GEO_LOCATION_RESOURCE = new ConsoleResource(
            "pudans:event:resource:geo_location", "IP地理库");

    public static final ConsoleResource STORAGE_RESOURCE = new ConsoleResource(
            "pudans:event:resource:storage", "存储配置");

    public static final ConsoleResource SESSION_RESOURCE = new ConsoleResource(
            "pudans:event:resource:session", "会话管理");

    public static final ConsoleResource APP_GROUP_RESOURCE = new ConsoleResource(
            "pudans:event:resource:app_group", "应用分组管理");

    public static final ConsoleResource LOGIN_SESSION_RESOURCE = new ConsoleResource(
            "pudans:event:resource:login_session", "登录会话");

    public static final ConsoleResource SSO_TICKET_RESOURCE = new ConsoleResource(
            "pudans:event:resource:sso_ticket", "SSO票据");

    public static final ConsoleResource AUTHZ_POLICY_RESOURCE = new ConsoleResource(
            "pudans:event:resource:authz_policy", "统一授权策略");

    public static final ConsoleResource AUTHZ_ROLE_RESOURCE = new ConsoleResource(
            "pudans:event:resource:authz_role", "统一授权角色");

    public static final ConsoleResource AUTHZ_DECISION_RESOURCE = new ConsoleResource(
            "pudans:event:resource:authz_decision", "鉴权决策");

    public static final ConsoleResource SERVICE_ACTION_RESOURCE = new ConsoleResource(
            "pudans:event:resource:service_action", "服务动作");

    public static final ConsoleResource RESOURCE_TYPE_RESOURCE = new ConsoleResource(
            "pudans:event:resource:resource_type", "资源类型");

    public ConsoleResource(String code, String name) {
        super(code, name);
    }
}