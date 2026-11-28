package cn.tolim.pudans.mq.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum CompensationStatus {

    // No compensation has been attempted
    NONE("NONE"),

    // Every compensation handler completed successfully
    COMPENSATED("COMPENSATED"),

    // At least one compensation handler failed
    FAILED("FAILED");

    private final String code;

    CompensationStatus(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public static CompensationStatus fromCode(String code) {
        for (CompensationStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    public boolean canTransitionTo(CompensationStatus target) {
        if (target == null) {
            return false;
        }
        return switch (this) {
            case NONE -> target == COMPENSATED || target == FAILED;
            case FAILED -> target == COMPENSATED;
            case COMPENSATED -> false;
        };
    }

    @Override
    public String toString() {
        return code;
    }
}