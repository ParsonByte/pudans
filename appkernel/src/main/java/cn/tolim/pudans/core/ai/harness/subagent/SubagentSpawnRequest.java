package cn.tolim.pudans.core.ai.harness.subagent;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

public record SubagentSpawnRequest(
        String agentId,
        String task,
        boolean reusable,
        @Nullable String label,
        SubagentMode mode,
        @Nullable Duration timeout,
        String parentRunId) {

    public SubagentSpawnRequest {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("子代理标识不能为空。");
        }
        task = task == null ? "" : task;
        label = label == null || label.isBlank() ? null : label.trim();
        mode = mode == null ? SubagentMode.SYNC : mode;
        parentRunId = parentRunId == null ? "" : parentRunId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String agentId;

        private String task = "";

        private boolean reusable;

        private String label;

        private SubagentMode mode = SubagentMode.SYNC;

        private Duration timeout;

        private String parentRunId = "";

        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public Builder task(String task) {
            this.task = task;
            return this;
        }

        public Builder reusable(boolean reusable) {
            this.reusable = reusable;
            return this;
        }

        public Builder label(String label) {
            this.label = label;
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

        public SubagentSpawnRequest build() {
            return new SubagentSpawnRequest(
                    agentId, task, reusable, label, mode, timeout, parentRunId);
        }
    }
}