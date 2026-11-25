package cn.tolim.pudans.contract.agent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.Instant;
import java.util.UUID;

public record SandboxOperation(
        UUID id,
        UUID sandboxId,
        String operationType,
        String command,
        Status status,
        Integer exitCode,
        String stdoutRef,
        String stderrRef,
        boolean outputTruncated,
        UUID approvalId,
        int timeoutSeconds,
        Instant startedAt,
        Instant finishedAt,
        String errorCode,
        String errorMessage) {

    public enum Status {
        RUNNING("running"),
        SUCCEEDED("succeeded"),
        FAILED("failed"),
        TIMED_OUT("timed_out");

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
            throw new IllegalArgumentException("不支持的沙箱操作状态：" + code);
        }
    }
}
