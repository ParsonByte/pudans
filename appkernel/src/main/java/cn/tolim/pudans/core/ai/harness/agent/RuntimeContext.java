package cn.tolim.pudans.core.ai.harness.agent;

import java.util.Map;
import java.util.Objects;

public record RuntimeContext(String userId, String sessionId, Map<String, Object> metadata) {

    public RuntimeContext {
        userId = Objects.requireNonNull(userId, "用户标识不能为空");
        sessionId = Objects.requireNonNull(sessionId, "会话标识不能为空");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static RuntimeContext of(String userId, String sessionId) {
        return new RuntimeContext(userId, sessionId, Map.of());
    }

    public static Builder builder() {
        return new Builder();
    }

    public String key() {
        return userId + ":" + sessionId;
    }

    public static final class Builder {

        private String userId;

        private String sessionId;

        private Map<String, Object> metadata = Map.of();

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public RuntimeContext build() {
            return new RuntimeContext(userId, sessionId, metadata);
        }
    }
}