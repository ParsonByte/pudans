package cn.tolim.pudans.job.support;

import cn.tolim.pudans.communal.core.error.ApiException;

public final class JobErrors {

    // Error codes returned to API clients
    public static final String JOB_NOT_FOUND = "JOB_NOT_FOUND";
    public static final String JOB_CODE_EXISTS = "JOB_CODE_EXISTS";
    public static final String JOB_HANDLER_NOT_FOUND = "JOB_HANDLER_NOT_FOUND";
    public static final String JOB_SCHEDULE_INVALID = "JOB_SCHEDULE_INVALID";
    public static final String JOB_EXECUTION_NOT_FOUND = "JOB_EXECUTION_NOT_FOUND";
    public static final String JOB_EXECUTION_NOT_CANCELABLE = "JOB_EXECUTION_NOT_CANCELABLE";
    public static final String JOB_EXECUTOR_DISABLED = "JOB_EXECUTOR_DISABLED";
    public static final String JOB_INVALID_REQUEST = "JOB_INVALID_REQUEST";

    private JobErrors() {
    }

    // Job definition does not exist
    public static ApiException jobNotFound(String id) {
        return new ApiException(JOB_NOT_FOUND, 404, "Job definition not found: " + id);
    }

    // Job code is already used by another definition
    public static ApiException jobCodeExists(String code) {
        return new ApiException(JOB_CODE_EXISTS, 409, "Job code already exists: " + code);
    }

    // No handler is registered under the given name
    public static ApiException handlerNotFound(String name) {
        return new ApiException(JOB_HANDLER_NOT_FOUND, 400, "No job handler registered: " + name);
    }

    // Schedule configuration is invalid
    public static ApiException invalidSchedule(String message) {
        return new ApiException(JOB_SCHEDULE_INVALID, 400, message);
    }

    // Execution record does not exist
    public static ApiException executionNotFound(String id) {
        return new ApiException(JOB_EXECUTION_NOT_FOUND, 404, "Job execution not found: " + id);
    }

    // Execution already finished or otherwise cannot be canceled
    public static ApiException executionNotCancelable(String id) {
        return new ApiException(JOB_EXECUTION_NOT_CANCELABLE, 409,
                "Job execution is not cancelable: " + id);
    }

    // The executor is globally disabled
    public static ApiException executorDisabled() {
        return new ApiException(JOB_EXECUTOR_DISABLED, 503, "Job executor is disabled");
    }

    // Request content is incomplete or inconsistent
    public static ApiException invalidRequest(String message) {
        return new ApiException(JOB_INVALID_REQUEST, 400, message);
    }
}