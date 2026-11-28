package cn.tolim.pudans.contract.job;

// Job executor API paths
public final class JobApiPaths {

    public static final String BASE = "/apis/pudans.tolim.cn/v1alpha1/job";

    public static final String JOBS_SUFFIX = "/jobs";
    public static final String JOBS = BASE + JOBS_SUFFIX;

    public static final String JOB_DETAIL_SUFFIX = "/jobs/{id}";
    public static final String JOB_DETAIL = BASE + JOB_DETAIL_SUFFIX;

    public static final String JOB_ENABLE_SUFFIX = "/jobs/{id}/enable";
    public static final String JOB_ENABLE = BASE + JOB_ENABLE_SUFFIX;

    public static final String JOB_DISABLE_SUFFIX = "/jobs/{id}/disable";
    public static final String JOB_DISABLE = BASE + JOB_DISABLE_SUFFIX;

    public static final String JOB_TRIGGER_SUFFIX = "/jobs/{id}/trigger";
    public static final String JOB_TRIGGER = BASE + JOB_TRIGGER_SUFFIX;

    public static final String JOB_HANDLERS_SUFFIX = "/jobs/handlers";
    public static final String JOB_HANDLERS = BASE + JOB_HANDLERS_SUFFIX;

    public static final String EXECUTIONS_SUFFIX = "/executions";
    public static final String EXECUTIONS = BASE + EXECUTIONS_SUFFIX;

    public static final String EXECUTION_DETAIL_SUFFIX = "/executions/{id}";
    public static final String EXECUTION_DETAIL = BASE + EXECUTION_DETAIL_SUFFIX;

    public static final String EXECUTION_CANCEL_SUFFIX = "/executions/{id}/cancel";
    public static final String EXECUTION_CANCEL = BASE + EXECUTION_CANCEL_SUFFIX;

    public static final String EXECUTION_STATS_SUFFIX = "/executions/stats/{jobId}";
    public static final String EXECUTION_STATS = BASE + EXECUTION_STATS_SUFFIX;

    public static final String EXECUTOR_STATUS_SUFFIX = "/executor/status";
    public static final String EXECUTOR_STATUS = BASE + EXECUTOR_STATUS_SUFFIX;

    public static final String EXECUTOR_SCHEDULED_SUFFIX = "/executor/scheduled";
    public static final String EXECUTOR_SCHEDULED = BASE + EXECUTOR_SCHEDULED_SUFFIX;

    private JobApiPaths() {
    }
}