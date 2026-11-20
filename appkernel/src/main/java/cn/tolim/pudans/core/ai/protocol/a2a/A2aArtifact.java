package cn.tolim.pudans.core.ai.protocol.a2a;

import java.util.List;

public record A2aArtifact(String artifactId, List<A2aPart> parts) {

    public A2aArtifact {
        parts = List.copyOf(parts);
    }

    public static A2aArtifact ofText(String artifactId, String text) {
        return new A2aArtifact(artifactId, List.of(A2aPart.ofText(text)));
    }
}