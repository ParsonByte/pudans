package cn.tolim.pudans.core.ai.runtime.sandbox;

import java.time.Instant;

public record SandboxLogEvent(Instant timestamp, String stream, String line) {

    public SandboxLogEvent {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        stream = stream == null || stream.isBlank() ? "stdout" : stream;
        line = line == null ? "" : line;
    }
}