package cn.tolim.pudans.iam.contract;

import cn.tolim.pudans.contract.iam.IamContractPaths;
import cn.tolim.pudans.iam.infra.constant.IamConstants;

public final class IamAuthApiPaths {

    // Service root, all endpoints are served below this prefix
    public static final String BASE = IamConstants.API_PREFIX;

    public static final String LOGIN = IamContractPaths.AUTH_LOGIN;
    public static final String LOGIN_CONTRACT = IamContractPaths.AUTH_LOGIN;

    public static final String LOGIN_CONFIG = IamContractPaths.AUTH_LOGIN_CONFIG;
    public static final String LOGIN_CONFIG_CONTRACT = IamContractPaths.AUTH_LOGIN_CONFIG;

    public static final String OTP_SEND = IamContractPaths.AUTH_OTP_SEND;
    public static final String OTP_LOGIN = IamContractPaths.AUTH_OTP_LOGIN;

    public static final String PASSKEY_OPTIONS = IamContractPaths.AUTH_PASSKEY_OPTIONS;
    public static final String PASSKEY_VERIFY = IamContractPaths.AUTH_PASSKEY_VERIFY;

    public static final String IDP_QR_START = IamContractPaths.AUTH_IDP_QR_START;
    public static final String IDP_QR_POLL = IamContractPaths.AUTH_IDP_QR_POLL;
    public static final String IDP_QR_CALLBACK = IamContractPaths.AUTH_IDP_QR_CALLBACK;
    public static final String IDP_QR_SCAN = IamContractPaths.AUTH_IDP_QR_SCAN;

    public static final String FORGOT_PASSWORD = IamContractPaths.AUTH_FORGOT_PASSWORD;
    public static final String RESET_PASSWORD = IamContractPaths.AUTH_RESET_PASSWORD;

    public static final String LOGOUT = IamContractPaths.AUTH_LOGOUT;
    public static final String LOGOUT_CONTRACT = IamContractPaths.AUTH_LOGOUT;

    public static final String MFA_CHALLENGE = IamContractPaths.AUTH_MFA_CHALLENGE;
    public static final String MFA_CHALLENGE_CONTRACT = IamContractPaths.AUTH_MFA_CHALLENGE;

    public static final String MFA_VERIFY = IamContractPaths.AUTH_MFA_VERIFY;
    public static final String MFA_VERIFY_CONTRACT = IamContractPaths.AUTH_MFA_VERIFY;

    public static final String MFA_WEBAUTHN_OPTIONS = IamContractPaths.AUTH_MFA_WEBAUTHN_OPTIONS;
    public static final String MFA_WEBAUTHN_VERIFY = IamContractPaths.AUTH_MFA_WEBAUTHN_VERIFY;

    public static final String ME = IamContractPaths.ME;
    public static final String ME_CONTRACT = IamContractPaths.ME;

    public static final String ME_APPS = IamContractPaths.ME_APPS;
    public static final String ME_APPS_CONTRACT = IamContractPaths.ME_APPS;

    public static final String SESSION_VALIDATE = IamContractPaths.SESSION_VALIDATE;
    public static final String SESSION_VALIDATE_CONTRACT = IamContractPaths.SESSION_VALIDATE;
    public static final String SESSIONS_VALIDATE = IamContractPaths.SESSION_VALIDATE_ALIAS;

    public static final String SESSION_TOUCH = IamContractPaths.SESSION_TOUCH;
    public static final String SESSION_TOUCH_CONTRACT = IamContractPaths.SESSION_TOUCH;

    public static final String SESSION_REVOKE = IamContractPaths.SESSION_REVOKE;
    public static final String SESSION_REVOKE_CONTRACT = IamContractPaths.SESSION_REVOKE;

    public static final String SESSION_REVOKE_USER = IamContractPaths.SESSION_REVOKE_USER;
    public static final String SESSION_REVOKE_USER_CONTRACT = IamContractPaths.SESSION_REVOKE_USER;

    public static final String SESSION_DETAIL = IamContractPaths.SESSION_DETAIL;
    public static final String SESSION_DETAIL_CONTRACT = IamContractPaths.SESSION_DETAIL;

    public static final String SESSION_USER = IamContractPaths.SESSION_USER;
    public static final String SESSION_USER_CONTRACT = IamContractPaths.SESSION_USER;

    public static final String TICKET_ISSUE = IamContractPaths.TICKET_ISSUE;
    public static final String TICKET_ISSUE_PLURAL = IamContractPaths.TICKET_ISSUE_ALIAS;
    public static final String TICKET_ISSUE_CONTRACT = IamContractPaths.TICKET_ISSUE;

    public static final String TICKET_EXCHANGE = IamContractPaths.TICKET_EXCHANGE;
    public static final String TICKET_EXCHANGE_PLURAL = IamContractPaths.TICKET_EXCHANGE_ALIAS;
    public static final String TICKET_EXCHANGE_CONTRACT = IamContractPaths.TICKET_EXCHANGE;

    public static final String BACKCHANNEL_LOGOUT = IamContractPaths.BACKCHANNEL_LOGOUT;

    public static final String APP_ACCESS = IamContractPaths.APP_ACCESS;
    public static final String APP_ACCESS_CONTRACT = IamContractPaths.APP_ACCESS;

    public static final String APP_DETAIL = IamContractPaths.APP_DETAIL;
    public static final String APP_DETAIL_CONTRACT = IamContractPaths.APP_DETAIL;

    public static final String CSRF_PATH = IamConstants.CSRF_API_PATH;
    public static final String CAPTCHA_PATH = BASE + "/auth/captcha";
    public static final String LEGACY_CAPTCHA_PATH = BASE + "/captcha";

    public static final String PORTAL = BASE + "/portal";

    public static final String[] LOGIN_PATHS = {LOGIN};
    public static final String[] LOGIN_CONFIG_PATHS = {LOGIN_CONFIG};
    public static final String[] OTP_SEND_PATHS = {OTP_SEND};
    public static final String[] OTP_LOGIN_PATHS = {OTP_LOGIN};
    public static final String[] PASSKEY_OPTIONS_PATHS = {PASSKEY_OPTIONS};
    public static final String[] PASSKEY_VERIFY_PATHS = {PASSKEY_VERIFY};
    public static final String[] IDP_QR_START_PATHS = {IDP_QR_START};
    public static final String[] IDP_QR_POLL_PATHS = {IDP_QR_POLL};
    public static final String[] IDP_QR_CALLBACK_PATHS = {IDP_QR_CALLBACK};
    public static final String[] IDP_QR_SCAN_PATHS = {IDP_QR_SCAN};
    public static final String[] FORGOT_PASSWORD_PATHS = {FORGOT_PASSWORD};
    public static final String[] RESET_PASSWORD_PATHS = {RESET_PASSWORD};
    public static final String[] LOGOUT_PATHS = {LOGOUT};
    public static final String[] MFA_CHALLENGE_PATHS = {MFA_CHALLENGE};
    public static final String[] MFA_VERIFY_PATHS = {MFA_VERIFY};
    public static final String[] MFA_WEBAUTHN_OPTIONS_PATHS = {MFA_WEBAUTHN_OPTIONS};
    public static final String[] MFA_WEBAUTHN_VERIFY_PATHS = {MFA_WEBAUTHN_VERIFY};
    public static final String[] ME_PATHS = {ME};
    public static final String[] ME_APPS_PATHS = {ME_APPS};
    public static final String[] SESSION_VALIDATE_PATHS = {SESSION_VALIDATE, SESSIONS_VALIDATE};
    public static final String[] SESSION_TOUCH_PATHS = {SESSION_TOUCH};
    public static final String[] SESSION_REVOKE_PATHS = {SESSION_REVOKE};
    public static final String[] SESSION_REVOKE_USER_PATHS = {SESSION_REVOKE_USER};
    public static final String[] SESSION_DETAIL_PATHS = {SESSION_DETAIL};
    public static final String[] SESSION_USER_PATHS = {SESSION_USER};
    public static final String[] TICKET_ISSUE_PATHS = {TICKET_ISSUE, TICKET_ISSUE_PLURAL};
    public static final String[] TICKET_EXCHANGE_PATHS = {TICKET_EXCHANGE, TICKET_EXCHANGE_PLURAL};
    public static final String[] BACKCHANNEL_LOGOUT_PATHS = {BACKCHANNEL_LOGOUT};
    public static final String[] APP_ACCESS_PATHS = {APP_ACCESS};
    public static final String[] APP_DETAIL_PATHS = {APP_DETAIL};

    private IamAuthApiPaths() {
    }
}