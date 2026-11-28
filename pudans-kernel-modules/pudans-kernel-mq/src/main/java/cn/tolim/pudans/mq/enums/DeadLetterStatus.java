package cn.tolim.pudans.mq.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum DeadLetterStatus {

    // Parked and not yet resolved
    PENDING("PENDING"),

    // Requeued for another delivery round
    REPLAYED("REPLAYED"),

    // Resolved without replay
    DISCARDED("DISCARDED");

    private final String code;

    DeadLetterStatus(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public static DeadLetterStatus fromCode(String code) {
        for (DeadLetterStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    public boolean canTransitionTo(DeadLetterStatus target) {
        if (target == null) {
            return false;
        }
        return switch (this) {
            case PENDING -> target == REPLAYED || target == DISCARDED;
            case REPLAYED, DISCARDED -> false;
        };
    }

    public boolean isTerminal() {
        return this != PENDING;
    }

    @Override
    public String toString() {
        return code;
    }
}