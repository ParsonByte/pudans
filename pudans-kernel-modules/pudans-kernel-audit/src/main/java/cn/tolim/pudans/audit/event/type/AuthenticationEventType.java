package cn.tolim.pudans.audit.event.type;

import java.util.List;

import cn.tolim.pudans.audit.event.ConsoleResource;
import cn.tolim.pudans.audit.event.Type;
import cn.tolim.pudans.iam.infra.security.userdetails.UserType;
import static cn.tolim.pudans.audit.event.ConsoleResource.IDP_RESOURCE;

public class AuthenticationEventType {

    public static final Type ADD_IDP        = new Type("pudans:event:idp_add", "添加身份提供商", IDP_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type UPDATE_IDP     = new Type("pudans:event:idp_update", "修改身份提供商", IDP_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type ENABLE_IDP     = new Type("pudans:event:idp_enabled", "启用身份提供商", IDP_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type DISABLE_IDP    = new Type("pudans:event:idp_disabled", "禁用身份提供商", IDP_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type DELETE_IDP     = new Type("pudans:event:idp_delete", "删除身份提供商", IDP_RESOURCE,
        List.of(UserType.ADMIN));

    public static final Type LOGIN_CONSOLE  = new Type("pudans:event:login:console", "登录控制台",
        ConsoleResource.AUTHENTICATION_RESOURCE, List.of(UserType.ADMIN));

    public static final Type LOGOUT_CONSOLE = new Type("pudans:event:logout:console", "退出控制台",
        ConsoleResource.AUTHENTICATION_RESOURCE, List.of(UserType.ADMIN));

}
