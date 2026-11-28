package cn.tolim.pudans.contract.mq.enums;

// Lifecycle status of a dead letter
public enum DeadLetterStatus {
    PENDING,
    REPLAYED,
    DISCARDED
}