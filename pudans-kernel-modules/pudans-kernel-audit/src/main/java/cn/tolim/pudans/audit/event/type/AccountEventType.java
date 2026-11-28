package cn.tolim.pudans.audit.event.type;

import java.util.List;

import cn.tolim.pudans.audit.event.Type;
import static cn.tolim.pudans.audit.event.ConsoleResource.*;
import static cn.tolim.pudans.iam.infra.security.userdetails.UserType.ADMIN;

public class AccountEventType {

    public static final Type CREATE_USER                           = new Type(
        "pudans:event:account:create_user", "创建用户", ORG_ACCOUNT_RESOURCE, List.of(ADMIN));

    public static final Type UPDATE_USER                           = new Type(
        "pudans:event:account:update_user", "修改用户", ORG_ACCOUNT_RESOURCE, List.of(ADMIN));

    public static final Type DELETE_USER                           = new Type(
        "pudans:event:account:delete_user", "删除用户", ORG_ACCOUNT_RESOURCE, List.of(ADMIN));

    public static final Type DISABLE_USER                          = new Type(
        "pudans:event:account:disabled_user", "禁用用户", ORG_ACCOUNT_RESOURCE, List.of(ADMIN));

    public static final Type ENABLE_USER                           = new Type(
        "pudans:event:account:enabled_user", "启用用户", ORG_ACCOUNT_RESOURCE, List.of(ADMIN));

    public static final Type CREATE_ORG                            = new Type(
        "pudans:event:account:create_organization", "创建组织", ORG_ACCOUNT_RESOURCE, List.of(ADMIN));

    public static final Type UPDATE_ORG                            = new Type(
        "pudans:event:account:update_organization", "修改组织", ORG_ACCOUNT_RESOURCE, List.of(ADMIN));

    public static final Type DELETE_ORGANIZATION                   = new Type(
        "pudans:event:account:delete_organization", "删除组织", ORG_ACCOUNT_RESOURCE, List.of(ADMIN));

    public static final Type MOVE_ORGANIZATION                     = new Type(
        "pudans:event:account:move_organization", "移动组织", ORG_ACCOUNT_RESOURCE, List.of(ADMIN));

    public static final Type CREATE_ORGANIZATION_MEMBER            = new Type(
        "pudans:event:account:create_organization_member", "添加组织用户", ORG_ACCOUNT_RESOURCE,
        List.of(ADMIN));

    public static final Type USER_RESIGN                           = new Type(
        "pudans:event:account:user_resign", "用户离职", ORG_ACCOUNT_RESOURCE, List.of(ADMIN));

    public static final Type MODIFY_USER_EMAIL                     = new Type(
        "pudans:event:account:update_email", "修改邮箱", ORG_ACCOUNT_RESOURCE, List.of(ADMIN));

    public static final Type MODIFY_USER_PHONE                     = new Type(
        "pudans:event:account:update_phone", "修改手机号", ORG_ACCOUNT_RESOURCE, List.of(ADMIN));

    public static final Type MODIFY_USER_PASSWORD                  = new Type(
        "pudans:event:account:update_password", "修改密码", ORG_ACCOUNT_RESOURCE, List.of(ADMIN));

    public static final Type UNLOCK_USER                           = new Type(
        "pudans:event:account:unlock_user", "解锁用户", ORG_ACCOUNT_RESOURCE, List.of(ADMIN));

    public static final Type CREATE_USER_GROUP                     = new Type(
        "pudans:event:account:create_user_group", "创建静态用户组", USER_GROUP_RESOURCE, List.of(ADMIN));

    public static final Type UPDATE_USER_GROUP                     = new Type(
        "pudans:event:account:update_user_group", "修改静态用户组", USER_GROUP_RESOURCE, List.of(ADMIN));

    public static final Type DELETE_USER_GROUP                     = new Type(
        "pudans:event:account:delete_user_group", "删除静态用户组", USER_GROUP_RESOURCE, List.of(ADMIN));

    public static final Type ADD_USER_GROUP_MEMBER                 = new Type(
        "pudans:event:account:add_user_group_member", "添加静态用户组成员", USER_GROUP_RESOURCE,
        List.of(ADMIN));

    public static final Type REMOVE_USER_GROUP_MEMBER              = new Type(
        "pudans:event:account:remove_user_group_member", "移除静态用户组成员", USER_GROUP_RESOURCE,
        List.of(ADMIN));

    public static final Type CREATE_USER_DYNAMIC_GROUP             = new Type(
        "pudans:event:account:create_user_dynamic_group", "创建动态用户组", USER_GROUP_RESOURCE,
        List.of(ADMIN));

    public static final Type UPDATE_USER_DYNAMIC_GROUP             = new Type(
        "pudans:event:account:update_user_dynamic_group", "修改动态用户组", USER_GROUP_RESOURCE,
        List.of(ADMIN));

    public static final Type UPDATE_USER_DYNAMIC_GROUP_FILTER_RULE = new Type(
        "pudans:event:account:update_user_dynamic_group:filter_rule", "编辑动态用户组过滤规则",
        USER_GROUP_RESOURCE, List.of(ADMIN));

    public static final Type DELETE_USER_DYNAMIC_GROUP             = new Type(
        "pudans:event:account:delete_user_dynamic_group", "删除动态用户组", USER_GROUP_RESOURCE,
        List.of(ADMIN));

    public static final Type CREATE_IDENTITY_RESOURCE              = new Type(
        "pudans:event:account:create_identity_resource", "创建身份源", IDENTITY_SOURCE_RESOURCE,
        List.of(ADMIN));

    public static final Type UPDATE_IDENTITY_RESOURCE              = new Type(
        "pudans:event:account:update_identity_resource", "修改身份源", IDENTITY_SOURCE_RESOURCE,
        List.of(ADMIN));

    public static final Type DELETE_IDENTITY_RESOURCE              = new Type(
        "pudans:event:account:delete_identity_resource", "删除身份源", IDENTITY_SOURCE_RESOURCE,
        List.of(ADMIN));

    public static final Type ENABLE_IDENTITY_RESOURCE              = new Type(
        "pudans:event:account:enable_identity_resource", "启用身份源", IDENTITY_SOURCE_RESOURCE,
        List.of(ADMIN));

    public static final Type DISABLE_IDENTITY_RESOURCE             = new Type(
        "pudans:event:account:disable_identity_resource", "禁用身份源", IDENTITY_SOURCE_RESOURCE,
        List.of(ADMIN));

    public static final Type IDENTITY_RESOURCE_SYNC                = new Type(
        "pudans:event:account:identity_resource_sync", "身份源同步", IDENTITY_SOURCE_RESOURCE,
        List.of(ADMIN));

}
