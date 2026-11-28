package cn.tolim.pudans.job.handler;

// Outcome returned by a job handler
public record JobHandlerResult(boolean success, String message) {

    // Successful outcome without additional detail
    public static JobHandlerResult ok() {
        return new JobHandlerResult(true, null);
    }

    // Successful outcome with a detail message
    public static JobHandlerResult ok(String message) {
        return new JobHandlerResult(true, message);
    }

    // Failed outcome with a reason
    public static JobHandlerResult fail(String message) {
        return new JobHandlerResult(false, message);
    }
}