package cn.tolim.pudans.contract.mq.enums;

// Lifecycle status of a message
public enum MessageStatus {
    PENDING,
    DISPATCHING,
    DELIVERED,
    RETRYING,
    CONSUMED,
    DEAD_LETTER,
    CANCELLED
}