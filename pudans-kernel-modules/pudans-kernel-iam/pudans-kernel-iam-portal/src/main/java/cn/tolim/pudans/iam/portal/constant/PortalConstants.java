package cn.tolim.pudans.iam.portal.constant;

import cn.tolim.pudans.iam.infra.constant.IamConstants;

public final class PortalConstants {

    // Portal API path inside the IAM API namespace
    public static final String PORTAL_API_PATH = IamConstants.V1_API_PATH + "/portal";

    // Fallback global login session cookie name, overridden by the IAM auth properties when present
    public static final String DEFAULT_SESSION_COOKIE_NAME = "PUDANS_SID";

    // Setting keys for login page branding
    public static final String SETTING_LOGIN_TITLE = "portal.login.title";
    public static final String SETTING_LOGIN_LOGO_URL = "portal.login.logo_url";
    public static final String SETTING_LOGIN_FAVICON_URL = "portal.login.favicon_url";
    public static final String SETTING_LOGIN_BACKGROUND_URL = "portal.login.background_url";

    // Setting keys for login methods
    public static final String SETTING_LOGIN_PASSWORD_ENABLED = "portal.login.password_enabled";
    public static final String SETTING_LOGIN_SMS_ENABLED = "portal.login.sms_enabled";
    public static final String SETTING_LOGIN_MAIL_ENABLED = "portal.login.mail_enabled";
    public static final String SETTING_LOGIN_TOTP_ENABLED = "portal.login.totp_enabled";
    public static final String SETTING_LOGIN_WEBAUTHN_ENABLED = "portal.login.webauthn_enabled";

    // Setting keys for login behavior
    public static final String SETTING_LOGIN_CAPTCHA_ENABLED = "portal.login.captcha_enabled";
    public static final String SETTING_LOGIN_MFA_REQUIRED = "portal.login.mfa_required";
    public static final String SETTING_LOGIN_REMEMBER_ME_ENABLED = "portal.login.remember_me_enabled";

    // Default session lifetime in seconds, matches the security setting default
    public static final long DEFAULT_SESSION_VALID_TIME_SECONDS = 18000L;

    // Login method codes
    public static final String METHOD_PASSWORD = "password";
    public static final String METHOD_SMS = "sms";
    public static final String METHOD_MAIL = "mail";
    public static final String METHOD_TOTP = "totp";
    public static final String METHOD_WEBAUTHN = "webauthn";

    // Login method categories
    public static final String CATEGORY_PASSWORD = "password";
    public static final String CATEGORY_OTP = "otp";
    public static final String CATEGORY_MFA = "mfa";

    // Global logout method returned to the login page and homepage
    public static final String LOGOUT_METHOD_GLOBAL = "GLOBAL";

    private PortalConstants() {
    }
}