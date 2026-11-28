package cn.tolim.pudans.notification.enums;

// Lifecycle state of a notification send record.
public enum SendStatus {
    // Stored and waiting for the first delivery attempt or an asynchronous retry.
    PENDING,
    // Delivered successfully.
    SUCCESS,
    // Delivery failed and a retry is scheduled.
    FAILED,
    // Retries exhausted; the record was moved to the dead letter queue.
    DEAD_LETTER
}