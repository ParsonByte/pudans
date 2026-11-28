package cn.tolim.pudans.mq.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum MessageStatus {

    // Persisted and waiting for the next dispatch round
    PENDING("PENDING"),

    // Claimed by a dispatcher and currently being delivered
    DISPATCHING("DISPATCHING"),

    // Handed off to the transport without any registered subscriber
    DELIVERED("DELIVERED"),

    // At least one delivery failed and a retry is scheduled
    RETRYING("RETRYING"),

    // Every delivery was acknowledged
    CONSUMED("CONSUMED"),

    // Retry budget exhausted, the message is parked in the dead letter store
    DEAD_LETTER("DEAD_LETTER"),

    // Cancelled by an operator
    CANCELLED("CANCELLED");

    private final String code;

    MessageStatus(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public static MessageStatus fromCode(String code) {
        for (MessageStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    public boolean canTransitionTo(MessageStatus target) {
        if (target == null) {
            return false;
        }
        return switch (this) {
            case PENDING -> target == DISPATCHING || target == CANCELLED;
            case DISPATCHING -> target == DELIVERED || target == CONSUMED || target == RETRYING
                    || target == DEAD_LETTER || target == PENDING;
            case DELIVERED -> false;
            case RETRYING -> target == DISPATCHING || target == DEAD_LETTER || target == PENDING
                    || target == CANCELLED;
            case CONSUMED -> false;
            case DEAD_LETTER -> target == PENDING || target == CANCELLED;
            case CANCELLED -> false;
        };
    }

    public boolean isTerminal() {
        return this == DELIVERED || this == CONSUMED || this == CANCELLED;
    }

    @Override
    public String toString() {
        return code;
    }
}