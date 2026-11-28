package cn.tolim.pudans.iam.identitysource.feishu;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import static cn.tolim.pudans.iam.infra.constant.IamConstants.COLON;

// Constant
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FeiShuConstant {

    public static final String LOGGER_NAME = "feishu-identity";

    // Page size, maximum 50
    public static final int PAGE_SIZE = 50;
    // DingTalk Open API domain
    public static final String DOMAIN = "https://open.feishu.cn";

    // Get app_access_token (self-built application)
    // https://open.feishu.cn/document/ukTMukTMukTM/ukDNz4SO0MjL5QzM/auth-v3/auth/app_access_token_internal
    public static final String APP_ACCESS_TOKEN_URL = DOMAIN
            + "/open-apis/auth/v3/app_access_token/internal";

    // Get tenant_access_token (self-built application)
    // https://open.feishu.cn/document/ukTMukTMukTM/ukDNz4SO0MjL5QzM/auth-v3/auth/tenant_access_token_internal
    public static final String ACCESS_TOKEN_URL = DOMAIN
            + "/open-apis/auth/v3/tenant_access_token/internal";
    // Recursively get child department list
    // https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/reference/contact-v3/department/children
    public static final String DEPARTMENT_LIST = DOMAIN
            + "/open-apis/contact/v3/departments/%s/children";
    // Get a single department
    // https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/reference/contact-v3/department/get
    public static final String DEPARTMENT_GET = DOMAIN + "/open-apis/contact/v3/departments/";
    // Get users directly under a department
    // https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/reference/contact-v3/user/find_by_department
    public static final String USER_LIST = DOMAIN
            + "/open-apis/contact/v3/users/find_by_department";
    // Get user information
    // https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/reference/contact-v3/user/get
    public static final String GET_USER = DOMAIN + "/open-apis/contact/v3/users/";
    // accessToken cache name
    public static final String FEI_SHU_ACCESS_TOKEN = "fei_shu_access_token" + COLON;
}
