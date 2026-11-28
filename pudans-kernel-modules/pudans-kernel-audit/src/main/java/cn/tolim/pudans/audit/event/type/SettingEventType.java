package cn.tolim.pudans.audit.event.type;

import java.util.List;

import cn.tolim.pudans.audit.event.Type;
import cn.tolim.pudans.iam.infra.security.userdetails.UserType;
import static cn.tolim.pudans.audit.event.ConsoleResource.*;

public class SettingEventType {

    public static final Type SAVE_LOGIN_SECURITY_BASIC_SETTINGS = new Type(
        "pudans:event:setting:save_security_basic", "保存安全基础设置", SECURITY_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type SAVE_PASSWORD_POLICY               = new Type(
        "pudans:event:setting:save_password_policy", "保存密码策略", PASSWORD_POLICY_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type UPDATE_PASSWORD_POLICY             = new Type(
        "pudans:event:setting:update_password_policy", "修改密码策略", PASSWORD_POLICY_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type DELETE_PASSWORD_POLICY             = new Type(
        "pudans:event:setting:delete_password_policy", "删除密码策略", PASSWORD_POLICY_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type ENABLE_PASSWORD_POLICY             = new Type(
        "pudans:event:setting:enable_password_policy", "启用密码策略", PASSWORD_POLICY_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type DISABLE_PASSWORD_POLICY            = new Type(
        "pudans:event:setting:disable_password_policy", "禁用密码策略", PASSWORD_POLICY_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type SORT_PASSWORD_POLICY               = new Type(
        "pudans:event:setting:sort_password_policy", "更新密码策略优先级", PASSWORD_POLICY_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type SAVE_SECURITY_POLICY_SETTINGS      = new Type(
        "pudans:event:setting:save_security_policy", "保存安全防御策略", SECURITY_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type ADD_ADMINISTRATOR_ROLE             = new Type(
        "pudans:event:setting:add_administrator_role", "添加角色", ADMINISTRATOR_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type DELETE_ADMINISTRATOR_ROLE          = new Type(
        "pudans:event:setting:delete_administrator_role", "删除角色", ADMINISTRATOR_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type UPDATE_ADMINISTRATOR_ROLE          = new Type(
        "pudans:event:setting:update_administrator_role", "修改角色", ADMINISTRATOR_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type ENABLE_ADMINISTRATOR_ROLE          = new Type(
        "pudans:event:setting:enable_administrator_role", "启用角色", ADMINISTRATOR_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type DISABLE_ADMINISTRATOR_ROLE         = new Type(
        "pudans:event:setting:disable_administrator_role", "禁用角色", ADMINISTRATOR_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type ADD_ADMINISTRATOR                  = new Type(
        "pudans:event:setting:add_administrator", "添加管理员", ADMINISTRATOR_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type DELETE_ADMINISTRATOR               = new Type(
        "pudans:event:setting:delete_administrator", "删除管理员", ADMINISTRATOR_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type UPDATE_ADMINISTRATOR               = new Type(
        "pudans:event:setting:update_administrator", "修改管理员", ADMINISTRATOR_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type ENABLE_ADMINISTRATOR               = new Type(
        "pudans:event:setting:enable_administrator", "启用管理员", ADMINISTRATOR_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type DISABLE_ADMINISTRATOR              = new Type(
        "pudans:event:setting:disable_administrator", "禁用管理员", ADMINISTRATOR_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type RESET_ADMINISTRATOR_PASSWORD       = new Type(
        "pudans:event:setting:reset_administrator_password", "重置管理员密码", ADMINISTRATOR_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type SAVE_MAIL_TEMPLATE                 = new Type(
        "pudans:event:setting:save_mail_template", "保存邮件模板", MESSAGE_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type OFF_MAIL_SERVICE                   = new Type(
        "pudans:event:setting:off_mail_service", "关闭邮件服务", MESSAGE_RESOURCE, List.of(UserType.ADMIN));

    public static final Type SAVE_MAIL_SERVICE                  = new Type(
        "pudans:event:setting:save_mail_service", "保存邮件服务", MESSAGE_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type OFF_SMS_SERVICE                    = new Type(
        "pudans:event:setting:off_sms_verify_service", "关闭短信验证服务", MESSAGE_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type SAVE_SMS_SERVICE                   = new Type(
        "pudans:event:setting:save_sms_verify_service", "保存短信验证服务", MESSAGE_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type OFF_STORAGE_SERVICE                = new Type(
        "pudans:event:setting:off_storage_service", "关闭存储服务", STORAGE_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type SAVE_STORAGE_SERVICE               = new Type(
        "pudans:event:setting:save_storage_service", "保存存储服务", STORAGE_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type OFF_GEO_LOCATION_SERVICE           = new Type(
        "pudans:event:setting:off_geoip_service", "关闭地理位置服务", GEO_LOCATION_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type SAVE_GEO_LOCATION_SERVICE          = new Type(
        "pudans:event:setting:save_geoip_service", "保存地理位置服务", GEO_LOCATION_RESOURCE,
        List.of(UserType.ADMIN));
}
