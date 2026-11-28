package cn.tolim.pudans.audit.event.type;

import java.util.List;

import cn.tolim.pudans.audit.event.ConsoleResource;
import cn.tolim.pudans.audit.event.Type;
import cn.tolim.pudans.iam.infra.security.userdetails.UserType;

public class SessionEventType {

    public static final Type SESSION_CREATED           = new Type("pudans:event:session:created",
        "创建登录会话", ConsoleResource.LOGIN_SESSION_RESOURCE, List.of(UserType.USER, UserType.ADMIN));

    public static final Type SESSION_EXPIRED           = new Type("pudans:event:session:expired",
        "登录会话过期", ConsoleResource.LOGIN_SESSION_RESOURCE, List.of(UserType.USER, UserType.ADMIN));

    public static final Type SESSION_REVOKED           = new Type("pudans:event:session:revoked",
        "登录会话失效", ConsoleResource.LOGIN_SESSION_RESOURCE, List.of(UserType.USER, UserType.ADMIN));

    public static final Type SESSION_KICKED            = new Type("pudans:event:session:kicked",
        "登录会话被踢下线", ConsoleResource.LOGIN_SESSION_RESOURCE, List.of(UserType.USER, UserType.ADMIN));

    public static final Type SESSION_VALIDATION_FAILED = new Type(
        "pudans:event:session:validation_failed", "登录会话校验失败", ConsoleResource.LOGIN_SESSION_RESOURCE,
        List.of(UserType.USER, UserType.ADMIN));

    public static final Type SESSION_PASSWORD_CHANGED  = new Type(
        "pudans:event:session:password_changed", "修改密码导致登录会话失效",
        ConsoleResource.LOGIN_SESSION_RESOURCE, List.of(UserType.USER, UserType.ADMIN));

    public static final Type SESSION_USER_DISABLED     = new Type(
        "pudans:event:session:user_disabled", "账号禁用导致登录会话失效",
        ConsoleResource.LOGIN_SESSION_RESOURCE, List.of(UserType.USER, UserType.ADMIN));

    public static final Type SESSION_MFA_RESET         = new Type("pudans:event:session:mfa_reset",
        "MFA重置导致登录会话失效", ConsoleResource.LOGIN_SESSION_RESOURCE,
        List.of(UserType.USER, UserType.ADMIN));

    public static final Type APP_SESSION_REGISTERED    = new Type(
        "pudans:event:session:app_registered", "应用会话缓存注册", ConsoleResource.LOGIN_SESSION_RESOURCE,
        List.of(UserType.USER, UserType.ADMIN));

    public static final Type APP_SESSION_REVOKED       = new Type(
        "pudans:event:session:app_revoked", "应用会话缓存清除", ConsoleResource.LOGIN_SESSION_RESOURCE,
        List.of(UserType.USER, UserType.ADMIN));

}