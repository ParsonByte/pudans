package cn.tolim.pudans.job.handler;

// Contract for code that can be scheduled and executed by the job executor
public interface JobHandler {

    // Stable handler name referenced by job definitions
    String name();

    // Optional human readable description shown by the API
    default String description() {
        return "";
    }

    // Run one execution, throwing an exception is treated as a failure
    JobHandlerResult execute(JobExecutionContext context) throws Exception;
}