package cn.tolim.pudans.iam.communal.constant;

import cn.tolim.pudans.iam.infra.constant.IamConstants;


public final class SettingConstants {
    // System settings API path
    public final static String SETTING_PATH = IamConstants.V1_API_PATH + "/setting";

    // Security settings API path
    public final static String SECURITY_PATH = SETTING_PATH + "/security";

    // System settings cache name
    public static final String SETTING_CACHE_NAME = "setting";

    // Admin cache name
    public static final String ADMIN_CACHE_NAME = "admin";

    // Application AES key
    public static final String AES_SECRET = "security.aes_secret";

}
