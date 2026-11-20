package cn.tolim.pudans.core.ai.stats;

public final class AiUsageErrorCodes {

    public static final String UNAUTHENTICATED = "ai-usage-unauthenticated";

    public static final String DATE_INVALID = "ai-usage-date-invalid";

    public static final String DATE_RANGE_INVALID = "ai-usage-date-range-invalid";

    public static final String LIMIT_INVALID = "ai-usage-limit-invalid";

    public static final String CURSOR_INVALID = "ai-usage-cursor-invalid";

    public static final String CONVERSATION_INVALID = "ai-usage-conversation-invalid";

    public static final String REQUEST_NOT_FOUND = "ai-usage-request-not-found";

    public static final String SESSION_NOT_FOUND = "ai-usage-session-not-found";

    public static final String SCOPE_INVALID = "ai-usage-scope-invalid";

    public static final String BUCKET_INVALID = "ai-usage-bucket-invalid";

    public static final String GROUP_BY_INVALID = "ai-usage-group-by-invalid";

    public static final String METRIC_INVALID = "ai-usage-metric-invalid";

    public static final String PARAMETER_INVALID = "ai-usage-parameter-invalid";

    public static final String FORBIDDEN = "ai-usage-forbidden";

    public static final String BEFORE_REQUIRED = "ai-usage-before-required";

    private AiUsageErrorCodes() {}
}