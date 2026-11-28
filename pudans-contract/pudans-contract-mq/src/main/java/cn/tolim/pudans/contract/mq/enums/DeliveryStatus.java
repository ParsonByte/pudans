package cn.tolim.pudans.contract.mq.enums;

// Lifecycle status of one delivery attempt
public enum DeliveryStatus {
    PENDING,
    DELIVERING,
    CONSUMED,
    RETRYING,
    DEAD_LETTER,
    CANCELLED
}