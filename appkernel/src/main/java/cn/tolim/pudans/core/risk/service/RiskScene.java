package cn.tolim.pudans.core.risk.service;

public final class RiskScene {

    public static final String REQUEST_FLOOD = "request-flood";

    public static final String LOGIN_ATTEMPT = "login-attempt";

    public static final String LOGIN_FAILURE = "login-failure";

    public static final String TOTP_ATTEMPT = "totp-attempt";

    public static final String AUTH_FAILURE = "auth-failure";

    public static final String SERVER_ERROR = "server-error";

    public static final String SUDO_SEND = "sudo-send";

    public static final String SUDO_CONFIRM = "sudo-confirm";

    private RiskScene() {}
}
