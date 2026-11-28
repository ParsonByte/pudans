package cn.tolim.pudans.iam.communal.constant;

import cn.tolim.pudans.iam.infra.constant.IamConstants;


public final class SessionConstants {
    public final static String SESSION_PATH = IamConstants.V1_API_PATH + "/session";

    // CURRENT_USER
    public static final String CURRENT_USER = SESSION_PATH + "/current_user";

    // Current session state
    public static final String CURRENT_STATUS = SESSION_PATH + "/current_status";

}
