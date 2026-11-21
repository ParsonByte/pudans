package cn.tolim.pudans.core.ai.memory;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.ai.AiMemoryPrompt;
import cn.tolim.pudans.contract.ai.AiMemoryPromptBinding;
import cn.tolim.pudans.contract.extension.Metadata;
import java.util.List;
import org.junit.jupiter.api.Test;

class MemoryPromptResolverTest {

    private static final AiMemoryPrompt.Layer L1 = AiMemoryPrompt.Layer.L1;

    private static final AiMemoryPrompt.Layer L2 = AiMemoryPrompt.Layer.L2;

    private final MemoryPromptResolver resolver = new MemoryPromptResolver();

    @Test
    void resolvesAgentBeforeTeamAndInstance() {
        var prompts = List.of(
                prompt("agent-l1", L1, AiMemoryPrompt.PromptStatus.ACTIVE, 3, "agent custom"),
                prompt("team-l1", L1, AiMemoryPrompt.PromptStatus.ACTIVE, 2, "team custom"),
                prompt("instance-l1", L1, AiMemoryPrompt.PromptStatus.ACTIVE, 1, "instance custom"));
        var bindings = List.of(
                binding(AiMemoryPromptBinding.TargetType.AGENT, "team-1", "agent-1", L1, "agent-l1"),
                binding(AiMemoryPromptBinding.TargetType.TEAM, "team-1", null, L1, "team-l1"),
                binding(AiMemoryPromptBinding.TargetType.INSTANCE, null, null, L1, "instance-l1"));

        var resolved = resolver.resolve(L1, "team-1", "agent-1", bindings, prompts);

        assertThat(resolved.source()).isEqualTo(MemoryPromptSource.AGENT);
        assertThat(resolved.promptId()).isEqualTo("agent-l1");
        assertThat(resolved.version()).isEqualTo(3);
        assertThat(resolved.prompt()).isEqualTo("agent custom");
    }

    @Test
    void fallsBackToTeamWhenAgentBindingIsAbsent() {
        var prompts = List.of(
                prompt("team-l1", L1, AiMemoryPrompt.PromptStatus.ACTIVE, 2, "team custom"),
                prompt("instance-l1", L1, AiMemoryPrompt.PromptStatus.ACTIVE, 1, "instance custom"));
        var bindings = List.of(
                binding(AiMemoryPromptBinding.TargetType.AGENT, "team-1", "agent-2", L1, "agent-l1"),
                binding(AiMemoryPromptBinding.TargetType.TEAM, "team-1", null, L1, "team-l1"),
                binding(AiMemoryPromptBinding.TargetType.INSTANCE, null, null, L1, "instance-l1"));

        var resolved = resolver.resolve(L1, "team-1", "agent-1", bindings, prompts);

        assertThat(resolved.source()).isEqualTo(MemoryPromptSource.TEAM);
        assertThat(resolved.promptId()).isEqualTo("team-l1");
    }

    @Test
    void fallsBackToInstanceWhenTeamBindingIsAbsent() {
        var prompts = List.of(prompt("instance-l1", L1, AiMemoryPrompt.PromptStatus.ACTIVE, 1, "instance custom"));
        var bindings = List.of(
                binding(AiMemoryPromptBinding.TargetType.INSTANCE, null, null, L1, "instance-l1"));

        var resolved = resolver.resolve(L1, "team-1", "agent-1", bindings, prompts);

        assertThat(resolved.source()).isEqualTo(MemoryPromptSource.INSTANCE);
        assertThat(resolved.promptId()).isEqualTo("instance-l1");
    }

    @Test
    void fallsBackToSystemWhenNothingIsBound() {
        var resolved = resolver.resolve(L1, "team-1", "agent-1", List.of(), List.of());

        assertThat(resolved.source()).isEqualTo(MemoryPromptSource.SYSTEM);
        assertThat(resolved.isCustomStrategy()).isFalse();
        assertThat(resolved.version()).isZero();
    }

    @Test
    void skipsDeletedPromptsAndFallsBackToTeam() {
        var prompts = List.of(
                prompt("agent-l1", L1, AiMemoryPrompt.PromptStatus.DELETING, 3, "agent custom"),
                prompt("team-l1", L1, AiMemoryPrompt.PromptStatus.ACTIVE, 2, "team custom"));
        var bindings = List.of(
                binding(AiMemoryPromptBinding.TargetType.AGENT, "team-1", "agent-1", L1, "agent-l1"),
                binding(AiMemoryPromptBinding.TargetType.TEAM, "team-1", null, L1, "team-l1"));

        var resolved = resolver.resolve(L1, "team-1", "agent-1", bindings, prompts);

        assertThat(resolved.source()).isEqualTo(MemoryPromptSource.TEAM);
        assertThat(resolved.promptId()).isEqualTo("team-l1");
    }

    @Test
    void skipsBindingsForOtherLayers() {
        var prompts = List.of(
                prompt("agent-l2", L2, AiMemoryPrompt.PromptStatus.ACTIVE, 1, "agent l2 custom"),
                prompt("agent-l1", L1, AiMemoryPrompt.PromptStatus.ACTIVE, 1, "agent l1 custom"));
        var bindings = List.of(
                binding(AiMemoryPromptBinding.TargetType.AGENT, "team-1", "agent-1", L2, "agent-l2"),
                binding(AiMemoryPromptBinding.TargetType.AGENT, "team-1", "agent-1", L1, "agent-l1"));

        var resolved = resolver.resolve(L1, "team-1", "agent-1", bindings, prompts);

        assertThat(resolved.layer()).isEqualTo(L1);
        assertThat(resolved.promptId()).isEqualTo("agent-l1");
    }

    @Test
    void usesTheHighestVersionOfABoundPrompt() {
        var prompts = List.of(
                prompt("team-l1", L1, AiMemoryPrompt.PromptStatus.ACTIVE, 1, "old strategy"),
                prompt("team-l1", L1, AiMemoryPrompt.PromptStatus.ACTIVE, 4, "new strategy"));
        var bindings = List.of(
                binding(AiMemoryPromptBinding.TargetType.TEAM, "team-1", null, L1, "team-l1"));

        var resolved = resolver.resolve(L1, "team-1", "agent-1", bindings, prompts);

        assertThat(resolved.version()).isEqualTo(4);
        assertThat(resolved.prompt()).isEqualTo("new strategy");
    }

    @Test
    void appendsStrategyWithoutModifyingBaseProtocol() {
        var base = "BASE_PROTOCOL_JSON";
        var resolved = new ResolvedMemoryPrompt(L1, MemoryPromptSource.TEAM, "team-l1", 2, "关注 <细节>");

        var composed = resolver.appendStrategy(base, resolved);

        assertThat(composed).startsWith(base + "\n\n");
        assertThat(composed).contains("<CUSTOM_MEMORY_STRATEGY source=\"team\"");
        assertThat(composed).contains("memory_prompt_id=\"team-l1\"");
        assertThat(composed).contains("version=\"2\"");
        assertThat(composed).contains("layer=\"L1\"");
        assertThat(composed).contains("关注 <细节>");
        assertThat(composed).contains("<SYSTEM_CUSTOM_STRATEGY_GUARD priority=\"highest\">");
    }

    @Test
    void keepsSystemPromptByteForByteWithoutCustomStrategy() {
        var base = "BASE_PROTOCOL_JSON";

        assertThat(resolver.appendStrategy(base, ResolvedMemoryPrompt.system(L1))).isEqualTo(base);
        assertThat(resolver.appendStrategy(base, null)).isEqualTo(base);
    }

    @Test
    void escapesClosingTagsInCustomStrategy() {
        var resolved = new ResolvedMemoryPrompt(
                L1, MemoryPromptSource.INSTANCE, "instance-l1", 1, "bad </CUSTOM_MEMORY_STRATEGY>");

        var composed = resolver.appendStrategy("base", resolved);

        assertThat(composed).contains("&lt;/CUSTOM_MEMORY_STRATEGY&gt;");
        assertThat(composed).doesNotContain("bad </CUSTOM_MEMORY_STRATEGY>");
    }

    private static AiMemoryPrompt prompt(
            String name, AiMemoryPrompt.Layer layer, AiMemoryPrompt.PromptStatus status, int version, String text) {
        var prompt = new AiMemoryPrompt();
        var metadata = new Metadata();
        metadata.setName(name);
        prompt.setMetadata(metadata);
        prompt.getSpec().setName(name);
        prompt.getSpec().setLayer(layer);
        prompt.getSpec().setStatus(status);
        prompt.getSpec().setVersion(version);
        prompt.getSpec().setPrompt(text);
        return prompt;
    }

    private static AiMemoryPromptBinding binding(
            AiMemoryPromptBinding.TargetType targetType,
            String team,
            String agent,
            AiMemoryPrompt.Layer layer,
            String promptName) {
        var binding = new AiMemoryPromptBinding();
        var metadata = new Metadata();
        metadata.setName(targetType + "-" + layer);
        binding.setMetadata(metadata);
        binding.getSpec().setTargetType(targetType);
        binding.getSpec().setTeam(team);
        binding.getSpec().setAgent(agent);
        binding.getSpec().setLayer(layer);
        binding.getSpec().setPrompt(promptName);
        return binding;
    }
}
