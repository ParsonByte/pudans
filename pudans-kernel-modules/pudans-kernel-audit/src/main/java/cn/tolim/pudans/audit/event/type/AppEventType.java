package cn.tolim.pudans.audit.event.type;

import java.util.List;

import cn.tolim.pudans.audit.event.Type;
import cn.tolim.pudans.iam.infra.security.userdetails.UserType;
import static cn.tolim.pudans.audit.event.ConsoleResource.APP_GROUP_RESOURCE;
import static cn.tolim.pudans.audit.event.ConsoleResource.APP_RESOURCE;

public class AppEventType {

    public static final Type ADD_APP                         = new Type("pudans:event:app:create", "添加应用",
        APP_RESOURCE, List.of(UserType.ADMIN));

    public static final Type ENABLE_APP                      = new Type("pudans:event:app:enabled", "启用应用",
        APP_RESOURCE, List.of(UserType.ADMIN));

    public static final Type DISABLE_APP                     = new Type("pudans:event:app:disabled", "禁用应用",
        APP_RESOURCE, List.of(UserType.ADMIN));

    public static final Type UPDATE_APP                      = new Type("pudans:event:app:update", "修改应用",
        APP_RESOURCE, List.of(UserType.ADMIN));

    public static final Type SAVE_APP_CONFIG                 = new Type("pudans:event:app:save:config",
        "保存应用配置", APP_RESOURCE, List.of(UserType.ADMIN));

    public static final Type DELETE_APP                      = new Type("pudans:event:app:delete", "删除应用",
        APP_RESOURCE, List.of(UserType.ADMIN));

    public static final Type APP_ACCESS_POLICY               = new Type("pudans:event:app:access_policy",
        "添加应用访问授权", APP_RESOURCE, List.of(UserType.ADMIN));

    public static final Type ENABLE_APP_ACCESS_POLICY        = new Type(
        "pudans:event:app:enable_access_policy", "启用应用访问授权", APP_RESOURCE, List.of(UserType.ADMIN));

    public static final Type DISABLE_APP_ACCESS_POLICY       = new Type(
        "pudans:event:app:disable_access_policy", "禁用应用访问授权", APP_RESOURCE, List.of(UserType.ADMIN));

    public static final Type APP_CANCEL_ACCESS_POLICY        = new Type(
        "pudans:event:app:cancel_access_policy", "取消应用访问授权", APP_RESOURCE, List.of(UserType.ADMIN));

    public static final Type ADD_APP_ACCOUNT                 = new Type("pudans:event:app:add_app_account",
        "添加应用账户", APP_RESOURCE, List.of(UserType.ADMIN));

    public static final Type UPDATE_DEFAULT_APP_ACCOUNT      = new Type(
        "pudans:event:app:update_default_app_account", "更新默认应用账户", APP_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type DELETE_APP_ACCOUNT              = new Type(
        "pudans:event:app:delete_app_account", "删除应用账户", APP_RESOURCE, List.of(UserType.ADMIN));

    public static final Type SAVE_SSO_CONFIG                 = new Type("pudans:event:app:save_sso_config",
        "保存SSO配置", APP_RESOURCE, List.of(UserType.ADMIN));

    public static final Type SAVE_APP_PERMISSION_RESOURCE    = new Type(
        "pudans:event:app:save_app_permission_resource", "保存应用权限资源", APP_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type UPDATE_APP_PERMISSION_RESOURCE  = new Type(
        "pudans:event:app:update_app_permission_resource", "修改应用权限资源", APP_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type DELETE_APP_PERMISSION_RESOURCE  = new Type(
        "pudans:event:app:delete_app_permission_resource", "删除应用权限资源", APP_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type ENABLE_APP_PERMISSION_RESOURCE  = new Type(
        "pudans:event:app:enable_app_permission_resource", "启用应用权限资源", APP_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type DISABLE_APP_PERMISSION_RESOURCE = new Type(
        "pudans:event:app:disable_app_permission_resource", "禁用应用权限资源", APP_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type DELETE_USER_IDP_BIND            = new Type(
        "pudans:event:app:delete_user_idp_bind", "删除用户身份提供商绑定", APP_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type SAVE_APP_PERMISSION_ROLE        = new Type(
        "pudans:event:app:save_app_permission_role", "添加应用角色", APP_RESOURCE, List.of(UserType.ADMIN));

    public static final Type UPDATE_APP_PERMISSION_ROLE      = new Type(
        "pudans:event:app:update_app_permission_role", "修改应用角色", APP_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type DELETE_APP_PERMISSION_ROLE      = new Type(
        "pudans:event:app:delete_app_permission_role", "删除应用角色", APP_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type ADD_APP_GROUP                   = new Type("pudans:event:app_group:create",
        "添加应用分组", APP_GROUP_RESOURCE, List.of(UserType.ADMIN));

    public static final Type UPDATE_APP_GROUP                = new Type("pudans:event:app_group:update",
        "修改应用分组", APP_GROUP_RESOURCE, List.of(UserType.ADMIN));

    public static final Type DELETE_APP_GROUP                = new Type("pudans:event:app_group:delete",
        "删除应用分组", APP_GROUP_RESOURCE, List.of(UserType.ADMIN));

    public static final Type REMOVE_APP_GROUP_ASSOCIATION    = new Type(
        "pudans:event:remove_app_group_association", "移除应用分组关联", APP_GROUP_RESOURCE,
        List.of(UserType.ADMIN));

}
