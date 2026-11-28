package cn.tolim.pudans.iam.infra.security.constant;

import static cn.tolim.pudans.iam.infra.constant.IamConstants.V1_API_PATH;

public final class SecurityConstants {

    public static final String FORM_LOGIN = V1_API_PATH + "/auth/login";
    public static final String LOGOUT_PATH = V1_API_PATH + "/auth/logout";
    public static final String LOGIN_PATH = V1_API_PATH + "/auth/login";
    public static final String REQUIRE_RESET_PASSWORD = "require_reset_password";

    private SecurityConstants() {
    }
}
