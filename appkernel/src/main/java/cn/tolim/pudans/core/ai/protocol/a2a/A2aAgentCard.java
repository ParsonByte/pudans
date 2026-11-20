package cn.tolim.pudans.core.ai.protocol.a2a;

import java.util.List;

public record A2aAgentCard(
        String name,
        String description,
        String url,
        String version,
        Capabilities capabilities,
        List<String> defaultInputModes,
        List<String> defaultOutputModes,
        List<Skill> skills) {

    public A2aAgentCard {
        defaultInputModes = List.copyOf(defaultInputModes);
        defaultOutputModes = List.copyOf(defaultOutputModes);
        skills = List.copyOf(skills);
    }

    public record Capabilities(boolean streaming, boolean pushNotifications) {}

    public record Skill(String id, String name, String description, List<String> tags) {}
}