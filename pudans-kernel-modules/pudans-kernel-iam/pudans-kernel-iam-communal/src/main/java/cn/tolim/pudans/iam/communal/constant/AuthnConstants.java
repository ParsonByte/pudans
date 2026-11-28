package cn.tolim.pudans.iam.communal.constant;

import cn.tolim.pudans.iam.infra.constant.IamConstants;

import static cn.tolim.pudans.iam.infra.security.constant.SecurityConstants.LOGIN_PATH;


public final class AuthnConstants {
    public final static String AUTHN_PATH = IamConstants.V1_API_PATH + "/authn";

    // Login configuration
    public static final String LOGIN_CONFIG = LOGIN_PATH + "/config";

    // Frontend login route
    public static final String FE_LOGIN = "/login";

}
