package cn.tolim.pudans.contract.iam;

// IAM service API paths
public final class IamContractPaths {

    public static final String BASE = "/apis/pudans.tolim.cn/v1alpha1/iam";

    public static final String AUTH_LOGIN_SUFFIX = "/auth/login";
    public static final String AUTH_LOGIN = BASE + AUTH_LOGIN_SUFFIX;

    public static final String AUTH_LOGOUT_SUFFIX = "/auth/logout";
    public static final String AUTH_LOGOUT = BASE + AUTH_LOGOUT_SUFFIX;

    public static final String AUTH_LOGIN_CONFIG_SUFFIX = "/auth/login/config";
    public static final String AUTH_LOGIN_CONFIG = BASE + AUTH_LOGIN_CONFIG_SUFFIX;

    public static final String AUTH_OTP_SEND_SUFFIX = "/auth/login/otp/send";
    public static final String AUTH_OTP_SEND = BASE + AUTH_OTP_SEND_SUFFIX;

    public static final String AUTH_OTP_LOGIN_SUFFIX = "/auth/login/otp";
    public static final String AUTH_OTP_LOGIN = BASE + AUTH_OTP_LOGIN_SUFFIX;

    public static final String AUTH_PASSKEY_OPTIONS_SUFFIX = "/auth/passkey/options";
    public static final String AUTH_PASSKEY_OPTIONS = BASE + AUTH_PASSKEY_OPTIONS_SUFFIX;

    public static final String AUTH_PASSKEY_VERIFY_SUFFIX = "/auth/passkey/verify";
    public static final String AUTH_PASSKEY_VERIFY = BASE + AUTH_PASSKEY_VERIFY_SUFFIX;

    public static final String AUTH_IDP_QR_START_SUFFIX = "/auth/idp/qr/start";
    public static final String AUTH_IDP_QR_START = BASE + AUTH_IDP_QR_START_SUFFIX;

    public static final String AUTH_IDP_QR_POLL_SUFFIX = "/auth/idp/qr/poll";
    public static final String AUTH_IDP_QR_POLL = BASE + AUTH_IDP_QR_POLL_SUFFIX;

    public static final String AUTH_IDP_QR_CALLBACK_SUFFIX = "/auth/idp/qr/callback";
    public static final String AUTH_IDP_QR_CALLBACK = BASE + AUTH_IDP_QR_CALLBACK_SUFFIX;

    public static final String AUTH_IDP_QR_SCAN_SUFFIX = "/auth/idp/qr/scan";
    public static final String AUTH_IDP_QR_SCAN = BASE + AUTH_IDP_QR_SCAN_SUFFIX;

    public static final String AUTH_MFA_CHALLENGE_SUFFIX = "/auth/mfa/challenge";
    public static final String AUTH_MFA_CHALLENGE = BASE + AUTH_MFA_CHALLENGE_SUFFIX;

    public static final String AUTH_MFA_VERIFY_SUFFIX = "/auth/mfa/verify";
    public static final String AUTH_MFA_VERIFY = BASE + AUTH_MFA_VERIFY_SUFFIX;

    public static final String AUTH_MFA_WEBAUTHN_OPTIONS_SUFFIX = "/auth/mfa/webauthn/options";
    public static final String AUTH_MFA_WEBAUTHN_OPTIONS =
            BASE + AUTH_MFA_WEBAUTHN_OPTIONS_SUFFIX;

    public static final String AUTH_MFA_WEBAUTHN_VERIFY_SUFFIX = "/auth/mfa/webauthn/verify";
    public static final String AUTH_MFA_WEBAUTHN_VERIFY = BASE + AUTH_MFA_WEBAUTHN_VERIFY_SUFFIX;

    public static final String AUTH_FORGOT_PASSWORD_SUFFIX = "/auth/password/forgot";
    public static final String AUTH_FORGOT_PASSWORD = BASE + AUTH_FORGOT_PASSWORD_SUFFIX;

    public static final String AUTH_RESET_PASSWORD_SUFFIX = "/auth/password/reset";
    public static final String AUTH_RESET_PASSWORD = BASE + AUTH_RESET_PASSWORD_SUFFIX;

    public static final String SESSION_VALIDATE_SUFFIX = "/session/validate";
    public static final String SESSION_VALIDATE = BASE + SESSION_VALIDATE_SUFFIX;

    public static final String SESSION_VALIDATE_ALIAS_SUFFIX = "/sessions/validate";
    public static final String SESSION_VALIDATE_ALIAS = BASE + SESSION_VALIDATE_ALIAS_SUFFIX;

    public static final String TICKET_ISSUE_ALIAS_SUFFIX = "/tickets/issue";
    public static final String TICKET_ISSUE_ALIAS = BASE + TICKET_ISSUE_ALIAS_SUFFIX;

    public static final String TICKET_EXCHANGE_ALIAS_SUFFIX = "/tickets/exchange";
    public static final String TICKET_EXCHANGE_ALIAS = BASE + TICKET_EXCHANGE_ALIAS_SUFFIX;

    public static final String SESSION_TOUCH_SUFFIX = "/session/touch";
    public static final String SESSION_TOUCH = BASE + SESSION_TOUCH_SUFFIX;

    public static final String SESSION_REVOKE_SUFFIX = "/session/revoke";
    public static final String SESSION_REVOKE = BASE + SESSION_REVOKE_SUFFIX;

    public static final String SESSION_REVOKE_USER_SUFFIX = "/session/revoke-user";
    public static final String SESSION_REVOKE_USER = BASE + SESSION_REVOKE_USER_SUFFIX;

    public static final String SESSION_DETAIL_SUFFIX = "/session/{sid}";
    public static final String SESSION_DETAIL = BASE + SESSION_DETAIL_SUFFIX;

    public static final String SESSION_USER_SUFFIX = "/session/user/{userId}";
    public static final String SESSION_USER = BASE + SESSION_USER_SUFFIX;

    public static final String TICKET_ISSUE_SUFFIX = "/ticket/issue";
    public static final String TICKET_ISSUE = BASE + TICKET_ISSUE_SUFFIX;

    public static final String TICKET_EXCHANGE_SUFFIX = "/ticket/exchange";
    public static final String TICKET_EXCHANGE = BASE + TICKET_EXCHANGE_SUFFIX;

    public static final String BACKCHANNEL_LOGOUT_SUFFIX = "/backchannel-logout";
    public static final String BACKCHANNEL_LOGOUT = BASE + BACKCHANNEL_LOGOUT_SUFFIX;

    public static final String AUTHZ_EVALUATE_SUFFIX = "/authz/evaluate";
    public static final String AUTHZ_EVALUATE = BASE + AUTHZ_EVALUATE_SUFFIX;

    public static final String AUTHZ_EVALUATE_BATCH_SUFFIX = "/authz/evaluate/batch";
    public static final String AUTHZ_EVALUATE_BATCH = BASE + AUTHZ_EVALUATE_BATCH_SUFFIX;

    public static final String AUTHZ_SIMULATE_SUFFIX = "/authz/simulate";
    public static final String AUTHZ_SIMULATE = BASE + AUTHZ_SIMULATE_SUFFIX;

    public static final String AUTHZ_EFFECTIVE_PERMISSIONS_SUFFIX =
            "/authz/principals/{principalType}/{principalId}/effective-permissions";
    public static final String AUTHZ_EFFECTIVE_PERMISSIONS = BASE + AUTHZ_EFFECTIVE_PERMISSIONS_SUFFIX;

    public static final String AUTHZ_LOGS_SUFFIX = "/authz/logs";
    public static final String AUTHZ_LOGS = BASE + AUTHZ_LOGS_SUFFIX;

    public static final String AUTHZ_ACTIONS_SUFFIX = "/authz/actions";
    public static final String AUTHZ_ACTIONS = BASE + AUTHZ_ACTIONS_SUFFIX;

    public static final String AUTHZ_RESOURCE_TYPES_SUFFIX = "/authz/resource-types";
    public static final String AUTHZ_RESOURCE_TYPES = BASE + AUTHZ_RESOURCE_TYPES_SUFFIX;

    public static final String USER_SUFFIX = "/users";
    public static final String USER = BASE + USER_SUFFIX;

    public static final String USER_DETAIL_SUFFIX = "/users/{userId}";
    public static final String USER_DETAIL = BASE + USER_DETAIL_SUFFIX;

    public static final String USER_BY_USERNAME_SUFFIX = "/users/by-username";
    public static final String USER_BY_USERNAME = BASE + USER_BY_USERNAME_SUFFIX;

    public static final String USER_BATCH_SUFFIX = "/users/batch";
    public static final String USER_BATCH = BASE + USER_BATCH_SUFFIX;

    public static final String USER_ROLES_SUFFIX = "/users/{userId}/roles";
    public static final String USER_ROLES = BASE + USER_ROLES_SUFFIX;

    public static final String ME_SUFFIX = "/me";
    public static final String ME = BASE + ME_SUFFIX;

    public static final String ME_APPS_SUFFIX = "/me/apps";
    public static final String ME_APPS = BASE + ME_APPS_SUFFIX;

    public static final String APP_SUFFIX = "/apps";
    public static final String APP = BASE + APP_SUFFIX;

    public static final String APP_DETAIL_SUFFIX = "/apps/{appCode}";
    public static final String APP_DETAIL = BASE + APP_DETAIL_SUFFIX;

    public static final String APP_ACCESS_SUFFIX = "/apps/{appCode}/access";
    public static final String APP_ACCESS = BASE + APP_ACCESS_SUFFIX;

    public static final String APP_REGISTER_SUFFIX = "/apps/registrations";
    public static final String APP_REGISTER = BASE + APP_REGISTER_SUFFIX;

    public static final String APP_ACTIONS_SUFFIX = "/apps/{appCode}/actions";
    public static final String APP_ACTIONS = BASE + APP_ACTIONS_SUFFIX;

    public static final String APP_RESOURCE_TYPES_SUFFIX = "/apps/{appCode}/resource-types";
    public static final String APP_RESOURCE_TYPES = BASE + APP_RESOURCE_TYPES_SUFFIX;

    private IamContractPaths() {
    }
}
