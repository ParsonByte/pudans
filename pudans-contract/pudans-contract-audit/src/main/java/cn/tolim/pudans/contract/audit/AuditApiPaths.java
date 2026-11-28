package cn.tolim.pudans.contract.audit;

// Audit service API paths
public final class AuditApiPaths {

    public static final String BASE = "/apis/pudans.tolim.cn/v1alpha1/iam/audit";

    public static final String RECORDS_SUFFIX = "/records";
    public static final String RECORDS = BASE + RECORDS_SUFFIX;

    public static final String RECORD_DETAIL_SUFFIX = "/records/{id}";
    public static final String RECORD_DETAIL = BASE + RECORD_DETAIL_SUFFIX;

    public static final String RECORDS_BY_REQUEST_SUFFIX = "/records/by-request/{requestId}";
    public static final String RECORDS_BY_REQUEST = BASE + RECORDS_BY_REQUEST_SUFFIX;

    public static final String RECORDS_BY_SESSION_SUFFIX = "/records/by-session/{sessionId}";
    public static final String RECORDS_BY_SESSION = BASE + RECORDS_BY_SESSION_SUFFIX;

    public static final String EVENT_TYPES_SUFFIX = "/event-types";
    public static final String EVENT_TYPES = BASE + EVENT_TYPES_SUFFIX;

    private AuditApiPaths() {
    }
}
