package cn.tolim.pudans.core.ai.memory;

import java.time.Instant;
import java.util.Objects;

public record CompletedTurn(MemoryContext context, String userText, String assistantText, Instant startedAt) {

    public CompletedTurn {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(userText, "userText");
        Objects.requireNonNull(assistantText, "assistantText");
        Objects.requireNonNull(startedAt, "startedAt");
    }
}
