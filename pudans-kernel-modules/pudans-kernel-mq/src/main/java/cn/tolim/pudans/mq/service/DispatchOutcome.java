package cn.tolim.pudans.mq.service;

public enum DispatchOutcome {

    // Another instance already claimed the message
    SKIPPED,

    // Handed off without registered subscribers
    DELIVERED,

    // Every subscriber acknowledged the message
    CONSUMED,

    // At least one subscriber failed and a retry is scheduled
    RETRYING,

    // Retry budget exhausted, the message is parked
    DEAD_LETTER
}