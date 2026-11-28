package cn.tolim.pudans.iam.identitysource.dingtalk;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import static cn.tolim.pudans.iam.infra.constant.IamConstants.COLON;

// Constant
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DingTalkConstants {
    // logger name
    public static final String LOGGER_NAME = "dingtalk-identity";
    // Chinese
    public static final String ZH_CN = "zh_CN";
    // Pagination
    public static final long CURSOR = 0L;
    // Page size, maximum 100
    public static final long SIZE = 100L;
    // DingTalk Open API domain
    public static final String DOMAIN = "https://oapi.dingtalk.com";
    // This API only returns basic information for first-level departments under the current department.
    public static final String DEPARTMENT_LIST = DOMAIN + "/topapi/v2/department/listsub";
    // This API gets details for the specified department by department ID.
    public static final String DEPARTMENT_GET = DOMAIN + "/topapi/v2/department/get";
    // This API only returns member details under the specified department; members in child departments are not included.
    public static final String USER_LIST = DOMAIN + "/topapi/v2/user/list";
    // Get user information
    public static final String GET_USER = DOMAIN + "/topapi/v2/user/get";

    // accessToken cache name
    public static final String DING_TALK_ACCESS_TOKEN = "ding_talk_access_token" + COLON;
}
