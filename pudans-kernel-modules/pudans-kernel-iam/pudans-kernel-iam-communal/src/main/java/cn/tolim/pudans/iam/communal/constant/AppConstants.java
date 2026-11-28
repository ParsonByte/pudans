package cn.tolim.pudans.iam.communal.constant;

import static cn.tolim.pudans.iam.infra.constant.IamConstants.COLON;
import static cn.tolim.pudans.iam.infra.constant.IamConstants.V1_API_PATH;


public final class AppConstants {

    // Application management API path
    public final static String APP_PATH = V1_API_PATH + "/app";

    // App configuration cache prefix
    public static final String APP_CACHE_NAME_PREFIX = "app" + COLON;

    // Group configuration cache prefix
    public static final String APP_GROUP_CACHE_NAME_PREFIX = "app_group" + COLON;

    // App basic information
    public static final String APP_CACHE_NAME = APP_CACHE_NAME_PREFIX + "basic";

    // Application account cache name
    public static final String APP_ACCOUNT_CACHE_NAME = APP_CACHE_NAME_PREFIX + "account";

    // OIDC configuration cache name
    public static final String OIDC_CONFIG_CACHE_NAME = APP_CACHE_NAME_PREFIX + "oidc";

    // APP Cert
    public static final String APP_CERT_CACHE_NAME = APP_CACHE_NAME_PREFIX + "cert";

    // FORM configuration cache name
    public static final String FORM_CONFIG_CACHE_NAME = APP_CACHE_NAME_PREFIX + "form";

    // JWT configuration cache name
    public static final String JWT_CONFIG_CACHE_NAME = APP_CACHE_NAME_PREFIX + "jwt";

}
