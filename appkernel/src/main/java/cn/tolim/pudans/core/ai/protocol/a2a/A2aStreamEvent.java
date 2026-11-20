package cn.tolim.pudans.core.ai.protocol.a2a;

import com.fasterxml.jackson.annotation.JsonProperty;

public sealed interface A2aStreamEvent {

    record Status(
            String kind,
            String taskId,
            String contextId,
            A2aTaskStatus status,
            @JsonProperty("final") boolean last) implements A2aStreamEvent {

        private static final String KIND = "status-update";

        public static Status of(String taskId, String contextId, A2aTaskStatus status, boolean last) {
            return new Status(KIND, taskId, contextId, status, last);
        }
    }

    record Artifact(
            String kind,
            String taskId,
            String contextId,
            A2aArtifact artifact,
            boolean append,
            boolean lastChunk) implements A2aStreamEvent {

        private static final String KIND = "artifact-update";

        public static Artifact of(String taskId, String contextId, A2aArtifact artifact) {
            return new Artifact(KIND, taskId, contextId, artifact, false, true);
        }
    }
}