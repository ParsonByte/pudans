package cn.tolim.pudans.core.ai.protocol;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum AiProtocol {
    CHAT_COMPLETIONS("chat-completions", "Chat Completions"),
    MESSAGES("messages", "Messages API"),
    RESPONSES("responses", "Responses API"),
    SYSTEM_ONE("systemone", "System One");

    private final String value;

    private final String displayName;

    AiProtocol(String value, String displayName) {
        this.value = value;
        this.displayName = displayName;
    }

    public String value() {
        return value;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<AiProtocol> find(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        var normalized = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(protocol -> protocol.value.equals(normalized)
                        || protocol.aliases().contains(normalized))
                .findFirst();
    }

    private java.util.Set<String> aliases() {
        return switch (this) {
            case CHAT_COMPLETIONS -> java.util.Set.of(
                    "chat-completion", "chatcompletions", "openai", "openai-compatible", "openai_compatible");
            case MESSAGES -> java.util.Set.of("anthropic", "anthropic-messages", "claude-messages");
            case RESPONSES -> java.util.Set.of("openai-responses", "responses-api");
            case SYSTEM_ONE -> java.util.Set.of("system-one", "system_one", "decision");
        };
    }
}
