package cn.tolim.pudans.iam.authentication.dingtalk.constant;


public final class DingTalkAuthenticationConstants {
    public static final String APP_ID = "appid";
    public static final String AUTH_CODE = "authCode";
    public static final String CORP_ID = "corpId";
    public static final String GET_USERINFO_BY_CODE = "https://oapi.dingtalk.com/sns/getuserinfo_bycode";
    public static final String SCAN_CODE_URL_AUTHORIZE = "https://oapi.dingtalk.com/connect/oauth2/sns_authorize";
    public static final String URL_AUTHORIZE = "https://login.dingtalk.com/oauth2/auth";

    public static final String GET_USERID_BY_UNIONID = "https://oapi.dingtalk.com/user/getUseridByUnionid";
    public static final String GET_USERINFO_BY_USERID = "https://oapi.dingtalk.com/topapi/v2/user/get";
}
