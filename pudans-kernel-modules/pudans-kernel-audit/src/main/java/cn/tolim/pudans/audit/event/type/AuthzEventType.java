package cn.tolim.pudans.audit.event.type;

import java.util.List;

import cn.tolim.pudans.audit.event.ConsoleResource;
import cn.tolim.pudans.audit.event.Type;
import cn.tolim.pudans.iam.infra.security.userdetails.UserType;

public class AuthzEventType {

    public static final Type AUTHZ_DECISION_ALLOW      = new Type(
        "pudans:event:authz:decision_allow", "鉴权通过", ConsoleResource.AUTHZ_DECISION_RESOURCE,
        List.of(UserType.USER, UserType.ADMIN));

    public static final Type AUTHZ_DECISION_DENY       = new Type(
        "pudans:event:authz:decision_deny", "鉴权拒绝", ConsoleResource.AUTHZ_DECISION_RESOURCE,
        List.of(UserType.USER, UserType.ADMIN));

    public static final Type POLICY_CREATE             = new Type("pudans:event:authz:policy_created",
        "创建授权策略", ConsoleResource.AUTHZ_POLICY_RESOURCE, List.of(UserType.ADMIN));

    public static final Type POLICY_UPDATE             = new Type("pudans:event:authz:policy_updated",
        "修改授权策略", ConsoleResource.AUTHZ_POLICY_RESOURCE, List.of(UserType.ADMIN));

    public static final Type POLICY_DELETE             = new Type("pudans:event:authz:policy_deleted",
        "删除授权策略", ConsoleResource.AUTHZ_POLICY_RESOURCE, List.of(UserType.ADMIN));

    public static final Type POLICY_ENABLE             = new Type("pudans:event:authz:policy_enabled",
        "启用授权策略", ConsoleResource.AUTHZ_POLICY_RESOURCE, List.of(UserType.ADMIN));

    public static final Type POLICY_DISABLE            = new Type("pudans:event:authz:policy_disabled",
        "禁用授权策略", ConsoleResource.AUTHZ_POLICY_RESOURCE, List.of(UserType.ADMIN));

    public static final Type POLICY_ATTACH             = new Type("pudans:event:authz:policy_attached",
        "绑定授权策略", ConsoleResource.AUTHZ_POLICY_RESOURCE, List.of(UserType.ADMIN));

    public static final Type POLICY_DETACH             = new Type("pudans:event:authz:policy_detached",
        "解绑授权策略", ConsoleResource.AUTHZ_POLICY_RESOURCE, List.of(UserType.ADMIN));

    public static final Type ROLE_CREATE               = new Type("pudans:event:authz:role_created",
        "创建授权角色", ConsoleResource.AUTHZ_ROLE_RESOURCE, List.of(UserType.ADMIN));

    public static final Type ROLE_UPDATE               = new Type("pudans:event:authz:role_updated",
        "修改授权角色", ConsoleResource.AUTHZ_ROLE_RESOURCE, List.of(UserType.ADMIN));

    public static final Type ROLE_DELETE               = new Type("pudans:event:authz:role_deleted",
        "删除授权角色", ConsoleResource.AUTHZ_ROLE_RESOURCE, List.of(UserType.ADMIN));

    public static final Type ROLE_BIND                 = new Type("pudans:event:authz:role_bound",
        "绑定授权角色", ConsoleResource.AUTHZ_ROLE_RESOURCE, List.of(UserType.ADMIN));

    public static final Type ROLE_UNBIND               = new Type("pudans:event:authz:role_unbound",
        "解绑授权角色", ConsoleResource.AUTHZ_ROLE_RESOURCE, List.of(UserType.ADMIN));

    public static final Type SERVICE_ACTION_REGISTER   = new Type(
        "pudans:event:authz:service_action_registered", "注册服务动作",
        ConsoleResource.SERVICE_ACTION_RESOURCE, List.of(UserType.ADMIN));

    public static final Type RESOURCE_TYPE_REGISTER    = new Type(
        "pudans:event:authz:resource_type_registered", "注册资源类型",
        ConsoleResource.RESOURCE_TYPE_RESOURCE, List.of(UserType.ADMIN));

}