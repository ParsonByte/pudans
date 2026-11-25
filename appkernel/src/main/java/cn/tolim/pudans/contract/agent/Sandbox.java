package cn.tolim.pudans.contract.agent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.Instant;
import java.util.UUID;

public record Sandbox(
        UUID id,
        String ownerId,
        String workspaceId,
        UUID agentSessionId,
        UUID runId,
        String provider,
        String template,
        String region,
        Status status,
        String workspaceRoot,
        Instant expiresAt,
        Instant lastHeartbeatAt,
        Instant createdAt,
        Instant startedAt,
        Instant stoppedAt,
        String errorCode,
        String errorMessage) {

    public enum Status {
        REQUESTED("requested"),
        PROVISIONING("provisioning"),
        READY("ready"),
        RUNNING("running"),
        SNAPSHOTTING("snapshotting"),
        STOPPED("stopped"),
        EXPIRED("expired"),
        FAILED("failed"),
        CANCELLED("cancelled"),
        RESUMING("resuming");

        private final String code;

        Status(String code) {
            this.code = code;
        }

        @JsonValue
        public String getCode() {
            return code;
        }

        public boolean terminal() {
            return this == STOPPED || this == EXPIRED || this == FAILED || this == CANCELLED;
        }

        public boolean acceptsCommand() {
            return this == READY || this == RUNNING;
        }

        public boolean acceptsSnapshot() {
            return this == READY || this == RUNNING;
        }

        @JsonCreator
        public static Status fromCode(String code) {
            for (var status : values()) {
                if (status.code.equalsIgnoreCase(code)) {
                    return status;
                }
            }
            throw new IllegalArgumentException("不支持的沙箱状态：" + code);
        }
    }
}
