package cn.tolim.pudans.audit.event.type;

import java.util.List;

import cn.tolim.pudans.audit.event.ConsoleResource;
import cn.tolim.pudans.audit.event.Type;
import cn.tolim.pudans.iam.infra.security.userdetails.UserType;

public class LoginEventType {

    public static final Type LOGIN_SUCCESS     = new Type("pudans:event:auth:login_success", "登录成功",
        ConsoleResource.AUTHENTICATION_RESOURCE, List.of(UserType.USER, UserType.ADMIN));

    public static final Type LOGIN_FAILURE     = new Type("pudans:event:auth:login_failure", "登录失败",
        ConsoleResource.AUTHENTICATION_RESOURCE, List.of(UserType.USER, UserType.ADMIN));

    public static final Type LOGIN_MFA_FAILURE = new Type("pudans:event:auth:login_mfa_failure",
        "MFA 验证失败", ConsoleResource.AUTHENTICATION_RESOURCE, List.of(UserType.USER, UserType.ADMIN));

    public static final Type LOGIN_LOCKED      = new Type("pudans:event:auth:login_locked", "登录失败锁定",
        ConsoleResource.AUTHENTICATION_RESOURCE, List.of(UserType.USER, UserType.ADMIN));

    public static final Type LOGOUT            = new Type("pudans:event:auth:logout", "全局退出登录",
        ConsoleResource.AUTHENTICATION_RESOURCE, List.of(UserType.USER, UserType.ADMIN));

}