package cn.tolim.pudans.iam.communal.constant;

import static cn.tolim.pudans.iam.infra.constant.IamConstants.ROOT_NODE;
import static cn.tolim.pudans.iam.infra.constant.IamConstants.V1_API_PATH;


public final class AccountConstants {

    // User API
    public final static String USER_PATH = V1_API_PATH + "/user";
    // Organization API
    public final static String ORGANIZATION_PATH = V1_API_PATH + "/organization";
    // User group API
    public final static String USER_GROUP_PATH = V1_API_PATH + "/user_group";

    // Identity source API
    public final static String IDENTITY_SOURCE_PATH = V1_API_PATH + "/identity_source";

    // Identity source cache name
    public static final String IDS_CACHE_NAME = "ids";

    // User cache name
    public static final String USER_CACHE_NAME = "user";
    // Organization cache name
    public static final String ORG_CACHE_NAME = "organization";

    // Root department ID
    public static final String ROOT_DEPT_ID = ROOT_NODE;

}
