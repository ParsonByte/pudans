package cn.tolim.pudans.job.support;

public final class JobConstants {

    // Root API path for the job executor
    public static final String API_PREFIX = "/apis/pudans.tolim.cn/v1alpha1/job";

    // Job definition API path
    public static final String JOBS_PATH = API_PREFIX + "/jobs";

    // Job execution API path
    public static final String EXECUTIONS_PATH = API_PREFIX + "/executions";

    // Executor status API path
    public static final String EXECUTOR_PATH = API_PREFIX + "/executor";

    // Package holding the job REST endpoints, used to scope the exception advice
    public static final String API_PACKAGE = "cn.tolim.pudans.job.contract";

    // Header used by the host service to pass the acting operator
    public static final String ACTOR_HEADER = "X-Pudans-Actor";

    // Default operator when no actor is supplied
    public static final String SYSTEM_ACTOR = "system";

    // Name of the built-in no-op handler
    public static final String NOOP_HANDLER = "noop";

    private JobConstants() {
    }
}