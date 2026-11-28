package cn.tolim.pudans.mq.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum DeliveryStatus {

    // Waiting for the next delivery attempt
    PENDING("PENDING"),

    // The consumer callback is running
    DELIVERING("DELIVERING"),

    // The consumer acknowledged the message
    CONSUMED("CONSUMED"),

    // The consumer failed and a retry is scheduled
    RETRYING("RETRYING"),

    // The consumer exhausted its retry budget
    DEAD_LETTER("DEAD_LETTER"),

    // Cancelled by an operator
    CANCELLED("CANCELLED");

    private final String code;

    DeliveryStatus(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public static DeliveryStatus fromCode(String code) {
        for (DeliveryStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    public boolean canTransitionTo(DeliveryStatus target) {
        if (target == null) {
            return false;
        }
        return switch (this) {
            case PENDING -> target == DELIVERING || target == CANCELLED;
            case DELIVERING -> target == CONSUMED || target == RETRYING || target == DEAD_LETTER;
            case CONSUMED -> false;
            case RETRYING -> target == DELIVERING || target == PENDING || target == CANCELLED
                    || target == DEAD_LETTER;
            case DEAD_LETTER -> target == PENDING || target == CANCELLED;
            case CANCELLED -> false;
        };
    }

    public boolean isTerminal() {
        return this == CONSUMED || this == CANCELLED;
    }

    @Override
    public String toString() {
        return code;
    }
}