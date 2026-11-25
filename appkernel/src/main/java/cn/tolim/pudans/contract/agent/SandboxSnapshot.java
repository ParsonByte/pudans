package cn.tolim.pudans.contract.agent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.Instant;
import java.util.UUID;

public record SandboxSnapshot(
        UUID id,
        UUID sandboxId,
        String provider,
        String snapshotId,
        String storageKind,
        String storageRoutePurpose,
        String storagePolicyId,
        String objectKey,
        Long sizeBytes,
        String checksum,
        Status status,
        Instant createdAt,
        Instant expiresAt) {

    public enum Status {
        CREATING("creating"),
        READY("ready"),
        FAILED("failed"),
        EXPIRED("expired"),
        DELETED("deleted");

        private final String code;

        Status(String code) {
            this.code = code;
        }

        @JsonValue
        public String getCode() {
            return code;
        }

        @JsonCreator
        public static Status fromCode(String code) {
            for (var status : values()) {
                if (status.code.equalsIgnoreCase(code)) {
                    return status;
                }
            }
            throw new IllegalArgumentException("不支持的沙箱快照状态：" + code);
        }
    }
}
