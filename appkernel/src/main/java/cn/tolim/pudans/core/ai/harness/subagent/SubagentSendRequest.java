package cn.tolim.pudans.core.ai.harness.subagent;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

public record SubagentSendRequest(
        @Nullable String agentKey,
        @Nullable String label,
        String message,
        SubagentMode mode,
        @Nullable Duration timeout,
        String parentRunId) {

    public SubagentSendRequest {
        agentKey = agentKey == null || agentKey.isBlank() ? null : agentKey.trim();
        label = label == null || label.isBlank() ? null : label.trim();
        message = message == null ? "" : message;
        mode = mode == null ? SubagentMode.SYNC : mode;
        parentRunId = parentRunId == null ? "" : parentRunId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String agentKey;

        private String label;

        private String message = "";

        private SubagentMode mode = SubagentMode.SYNC;

        private Duration timeout;

        private String parentRunId = "";

        public Builder agentKey(String agentKey) {
            this.agentKey = agentKey;
            return this;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder mode(SubagentMode mode) {
            this.mode = mode;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder parentRunId(String parentRunId) {
            this.parentRunId = parentRunId;
            return this;
        }

        public SubagentSendRequest build() {
            return new SubagentSendRequest(
                    agentKey, label, message, mode, timeout, parentRunId);
        }
    }
}