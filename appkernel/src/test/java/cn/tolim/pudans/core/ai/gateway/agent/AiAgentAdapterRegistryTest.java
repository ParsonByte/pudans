package cn.tolim.pudans.core.ai.gateway.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class AiAgentAdapterRegistryTest {

    private final ClaudeCodeAgentAdapter claudeCode = new ClaudeCodeAgentAdapter();

    private final OpenAiDefaultAgentAdapter openAiDefault = new OpenAiDefaultAgentAdapter();

    private final AiAgentAdapterRegistry registry =
            new AiAgentAdapterRegistry(List.of(claudeCode, openAiDefault), openAiDefault);

    @Test
    void resolvesKnownSourceCaseInsensitivelyAndFallsBackToDefault() {
        assertThat(registry.resolve("claude-code")).isSameAs(claudeCode);
        assertThat(registry.resolve("CLAUDE-CODE")).isSameAs(claudeCode);
        assertThat(registry.resolve("codex")).isSameAs(openAiDefault);
        assertThat(registry.resolve(null)).isSameAs(openAiDefault);
    }

    @Test
    void rejectsDuplicateSources() {
        assertThatThrownBy(() -> new AiAgentAdapterRegistry(
                        List.of(claudeCode, new ClaudeCodeAgentAdapter()), openAiDefault))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("claude-code");
    }
}
