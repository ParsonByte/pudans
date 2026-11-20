package cn.tolim.pudans.core.ai.protocol.a2a;

import java.util.List;

public record A2aTask(
        String id, String contextId, String kind, A2aTaskStatus status, List<A2aArtifact> artifacts) {

    public static final String KIND_TASK = "task";

    public A2aTask {
        artifacts = List.copyOf(artifacts);
    }

    public static A2aTask of(
            String id, String contextId, A2aTaskStatus status, List<A2aArtifact> artifacts) {
        return new A2aTask(id, contextId, KIND_TASK, status, artifacts);
    }
}