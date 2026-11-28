package cn.tolim.pudans.audit.constant;

import cn.tolim.pudans.iam.infra.constant.IamConstants;

public final class AuditApiPaths {

    public static final String AUDIT = IamConstants.API_PREFIX + "/audit";

    public static final String RECORDS = "/records";

    public static final String RECORD = "/records/{id}";

    public static final String RECORDS_BY_REQUEST = "/records/by-request/{requestId}";

    public static final String RECORDS_BY_SESSION = "/records/by-session/{sessionId}";

    public static final String EVENT_TYPES = "/event-types";

    private AuditApiPaths() {
    }
}