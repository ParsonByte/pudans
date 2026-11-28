package cn.tolim.pudans.audit.event.type;

import java.util.List;

import cn.tolim.pudans.audit.event.ConsoleResource;
import cn.tolim.pudans.audit.event.Type;
import cn.tolim.pudans.iam.infra.security.userdetails.UserType;
import static cn.tolim.pudans.audit.event.PortalResource.MY_ACCOUNT_RESOURCE;
import static cn.tolim.pudans.audit.event.PortalResource.MY_APP_RESOURCE;

public class PortalEventType {

    public static final Type APP_SSO                 = new Type("app_sso", "单点登录",
        MY_APP_RESOURCE, List.of(UserType.USER));

    public static final Type APP_SLO                 = new Type("app_slo", "单点登出",
        MY_APP_RESOURCE, List.of(UserType.USER));

    public static final Type BIND_IDP_USER           = new Type("pudans:event:account:bind_idp_user",
        "绑定 IDP", MY_ACCOUNT_RESOURCE, List.of(UserType.USER));

    public static final Type UNBIND_IDP_USER         = new Type("pudans:event:account:unbind_idp_user",
        "解绑 IDP", MY_ACCOUNT_RESOURCE, List.of(UserType.USER));

    public static final Type MODIFY_ACCOUNT_INFO     = new Type("pudans:event:account:update_account_info",
        "修改账户", MY_ACCOUNT_RESOURCE, List.of(UserType.USER));

    public static final Type MODIFY_USER_EMAIL       = new Type("pudans:event:account:update_email", "修改邮箱",
        MY_ACCOUNT_RESOURCE, List.of(UserType.USER));

    public static final Type MODIFY_USER_PHONE       = new Type("pudans:event:account:update_phone",
        "修改手机号", MY_ACCOUNT_RESOURCE, List.of(UserType.USER));

    public static final Type MODIFY_USER_PASSWORD    = new Type("pudans:event:account:update_password",
        "修改密码", MY_ACCOUNT_RESOURCE, List.of(UserType.USER));

    public static final Type BIND_MFA                = new Type("pudans:event:account:bind_mfa", "绑定 MFA",
        MY_ACCOUNT_RESOURCE, List.of(UserType.USER));

    public static final Type PREPARE_MODIFY_PASSWORD = new Type(
        "pudans:event:account:prepare_modify_password", "准备修改密码", MY_ACCOUNT_RESOURCE,
        List.of(UserType.USER));

    public static final Type PREPARE_MODIFY_PHONE    = new Type("pudans:event:account:prepare_modify_phone",
        "准备修改手机", MY_ACCOUNT_RESOURCE, List.of(UserType.USER));

    public static final Type PREPARE_MODIFY_EMAIL    = new Type("pudans:event:account:prepare_modify_email",
        "准备修改邮箱", MY_ACCOUNT_RESOURCE, List.of(UserType.USER));

    public static final Type PREPARE_BIND_MFA        = new Type("pudans:event:account:prepare_bind_mfa",
        "准备绑定MFA", MY_ACCOUNT_RESOURCE, List.of(UserType.USER));

    public static final Type UNBIND_MFA              = new Type("pudans:event:account:unbind_mfa", "解绑 MFA",
        MY_ACCOUNT_RESOURCE, List.of(UserType.USER));

    public static final Type LOGIN_PORTAL            = new Type("pudans:event:login:portal", "登录门户",
        ConsoleResource.AUTHENTICATION_RESOURCE, List.of(UserType.USER));

    public static final Type LOGOUT_PORTAL           = new Type("pudans:event:logout:portal", "退出门户",
        ConsoleResource.AUTHENTICATION_RESOURCE, List.of(UserType.USER));
}
