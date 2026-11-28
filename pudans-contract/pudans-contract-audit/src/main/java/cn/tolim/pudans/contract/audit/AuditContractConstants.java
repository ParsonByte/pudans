package cn.tolim.pudans.contract.audit;

// Shared constants for audit service-to-service contracts
public final class AuditContractConstants {

    public static final String SERVICE_NAME = "pudans-audit";
    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FAIL = "fail";
    public static final String CATEGORY_LOGIN = "login";
    public static final String CATEGORY_LOGOUT = "logout";
    public static final String CATEGORY_SESSION = "session";
    public static final String CATEGORY_TICKET = "ticket";
    public static final String CATEGORY_AUTHORIZATION = "authz";
    public static final String CATEGORY_ACCOUNT = "account";
    public static final String CATEGORY_APPLICATION = "application";
    public static final String CATEGORY_SETTING = "setting";
    public static final String CATEGORY_MONITOR = "monitor";
    public static final String CATEGORY_OTHER = "other";
    public static final String SORT_EVENT_TIME = "eventTime";
    public static final String SORT_CREATE_TIME = "createTime";
    public static final String SORT_ACTOR_ID = "actorId";
    public static final String SORT_EVENT_TYPE = "eventType";

    private AuditContractConstants() {
    }
}
